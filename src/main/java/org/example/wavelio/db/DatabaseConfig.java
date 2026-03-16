package org.example.wavelio.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

public final class DatabaseConfig {

    private final Path dbPath;

    public DatabaseConfig(Path dbPath) {
        this.dbPath = dbPath;
    }

    public static DatabaseConfig forUserHome() {
        String userHome = System.getProperty("user.home");
        Path dir = Path.of(userHome, "wavelio");
        Path dbFile = dir.resolve("wavelio.db");
        return new DatabaseConfig(dbFile);
    }

    public Connection openConnection() throws SQLException {
        ensureDirectory();
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        return DriverManager.getConnection(url);
    }

    public void initializeSchema() throws SQLException {
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS library_entry (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path TEXT NOT NULL UNIQUE,
                    display_name TEXT NOT NULL,
                    added_at INTEGER NOT NULL,
                    last_analyzed_at INTEGER
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS analysis_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entry_id INTEGER NOT NULL,
                    analyzed_at INTEGER NOT NULL,
                    description TEXT,
                    FOREIGN KEY (entry_id) REFERENCES library_entry(id) ON DELETE CASCADE
                )
                """);
        }
    }

    public long nowEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    private void ensureDirectory() {
        Path dir = dbPath.getParent();
        if (dir == null) {
            return;
        }
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create database directory: " + dir, e);
            }
        }
    }
}

