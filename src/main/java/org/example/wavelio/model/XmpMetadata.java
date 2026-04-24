package org.example.wavelio.model;

import java.util.Objects;

/**
 * Represents raw XMP packet extracted from a RIFF chunk.
 */
public final class XmpMetadata {

    private final String chunkId;
    private final String xml;

    public XmpMetadata(String chunkId, String xml) {
        this.chunkId = normalizeChunkId(chunkId);
        this.xml = xml == null ? "" : xml;
    }

    public String chunkId() {
        return chunkId;
    }

    public String xml() {
        return xml;
    }

    public boolean isEmpty() {
        return xml.isBlank();
    }

    private static String normalizeChunkId(String value) {
        if (value == null) {
            return "XMP ";
        }
        String normalized = value;
        if (normalized.length() > 4) {
            normalized = normalized.substring(0, 4);
        }
        if (normalized.length() < 4) {
            normalized = normalized + " ".repeat(4 - normalized.length());
        }
        return normalized;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XmpMetadata that)) return false;
        return Objects.equals(chunkId, that.chunkId) && Objects.equals(xml, that.xml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkId, xml);
    }
}

