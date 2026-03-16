package org.example.wavelio.repository;

import org.example.wavelio.model.LibraryEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface LibraryRepository {

    LibraryEntry upsertByPath(Path path, String displayName);

    Optional<LibraryEntry> findByPath(Path path);

    List<LibraryEntry> findAll();

    void deleteByPath(Path path);
}

