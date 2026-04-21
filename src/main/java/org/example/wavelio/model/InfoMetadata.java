package org.example.wavelio.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents RIFF INFO metadata as ordered FourCC->string map.
 */
public final class InfoMetadata {

    private final LinkedHashMap<String, String> fields;

    public InfoMetadata() {
        this.fields = new LinkedHashMap<>();
    }

    public InfoMetadata(Map<String, String> fields) {
        this.fields = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach(this::put);
        }
    }

    public Map<String, String> fields() {
        return Collections.unmodifiableMap(fields);
    }

    public Optional<String> get(String fourcc) {
        if (fourcc == null) return Optional.empty();
        return Optional.ofNullable(fields.get(fourcc));
    }

    public void put(String fourcc, String value) {
        String key = normalizeFourCC(fourcc);
        if (key == null) return;
        String v = value == null ? "" : value;
        fields.put(key, v);
    }

    public void remove(String fourcc) {
        String key = normalizeFourCC(fourcc);
        if (key == null) return;
        fields.remove(key);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    private static String normalizeFourCC(String fourcc) {
        if (fourcc == null) return null;
        String k = fourcc.trim();
        if (k.length() != 4) return null;
        return k;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InfoMetadata that)) return false;
        return Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }
}

