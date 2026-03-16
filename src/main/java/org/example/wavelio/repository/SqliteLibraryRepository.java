package org.example.wavelio.repository;

import org.example.wavelio.db.DatabaseConfig;
import org.example.wavelio.model.LibraryEntry;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteLibraryRepository implements LibraryRepository {

    private final DatabaseConfig config;

    public SqliteLibraryRepository(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public LibraryEntry upsertByPath(Path path, String displayName) {
        String filePath = path.toAbsolutePath().toString();
        long now = config.nowEpochMillis();

        try (Connection conn = config.openConnection()) {
            try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO library_entry (file_path, display_name, added_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(file_path) DO UPDATE SET display_name = excluded.display_name",
                Statement.RETURN_GENERATED_KEYS
            )) {
                insert.setString(1, filePath);
                insert.setString(2, displayName);
                insert.setLong(3, now);
                insert.executeUpdate();

                try (ResultSet rs = insert.getGeneratedKeys()) {
                    if (rs.next()) {
                        long id = rs.getLong(1);
                        return new LibraryEntry(
                            id,
                            path.toAbsolutePath(),
                            displayName,
                            Instant.ofEpochMilli(now),
                            null
                        );
                    }
                }
            }

            return findByPath(path).orElseThrow(() ->
                new IllegalStateException("Failed to upsert library entry for " + filePath));
        } catch (SQLException e) {
            throw new IllegalStateException("Error upserting library entry", e);
        }
    }

    @Override
    public Optional<LibraryEntry> findByPath(Path path) {
        String filePath = path.toAbsolutePath().toString();
        try (Connection conn = config.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, file_path, display_name, added_at, last_analyzed_at " +
                     "FROM library_entry WHERE file_path = ?")) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long id = rs.getLong("id");
                long addedAt = rs.getLong("added_at");
                long lastAnalyzedAt = rs.getLong("last_analyzed_at");
                Instant added = Instant.ofEpochMilli(addedAt);
                Instant lastAnalyzed = lastAnalyzedAt == 0 ? null : Instant.ofEpochMilli(lastAnalyzedAt);
                return Optional.of(new LibraryEntry(
                    id,
                    Path.of(rs.getString("file_path")),
                    rs.getString("display_name"),
                    added,
                    lastAnalyzed
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error finding library entry", e);
        }
    }

    @Override
    public List<LibraryEntry> findAll() {
        List<LibraryEntry> result = new ArrayList<>();
        try (Connection conn = config.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, file_path, display_name, added_at, last_analyzed_at FROM library_entry " +
                     "ORDER BY added_at DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    long addedAt = rs.getLong("added_at");
                    long lastAnalyzedAt = rs.getLong("last_analyzed_at");
                    Instant added = Instant.ofEpochMilli(addedAt);
                    Instant lastAnalyzed = lastAnalyzedAt == 0 ? null : Instant.ofEpochMilli(lastAnalyzedAt);
                    result.add(new LibraryEntry(
                        id,
                        Path.of(rs.getString("file_path")),
                        rs.getString("display_name"),
                        added,
                        lastAnalyzed
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error listing library entries", e);
        }
        return result;
    }

    @Override
    public void deleteByPath(Path path) {
        String filePath = path.toAbsolutePath().toString();
        try (Connection conn = config.openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM library_entry WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error deleting library entry", e);
        }
    }
}

