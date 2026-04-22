package org.example.wavelio.model;

public record SpectrogramResult(
    int sampleRate,
    int windowSize,
    int hopSize,
    double[] timeAxisSeconds,
    double[] frequencyAxisHz,
    double[][] magnitudesDb
) {}

