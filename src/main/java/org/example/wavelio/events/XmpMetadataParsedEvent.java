package org.example.wavelio.events;

import org.example.wavelio.model.XmpMetadata;

import java.util.Optional;

public record XmpMetadataParsedEvent(Optional<XmpMetadata> xmp) {}

