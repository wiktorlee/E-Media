package org.example.wavelio.events;

import org.example.wavelio.model.InfoMetadata;

import java.util.Optional;

public record InfoMetadataParsedEvent(Optional<InfoMetadata> info) {}

