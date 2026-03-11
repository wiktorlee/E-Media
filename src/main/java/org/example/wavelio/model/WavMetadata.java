package org.example.wavelio.model;

public record WavMetadata(
    int sampleRate,
    int numChannels,
    int bitsPerSample,
    long totalSamples,
    double durationSeconds
) {}
