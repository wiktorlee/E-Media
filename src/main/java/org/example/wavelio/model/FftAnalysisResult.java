package org.example.wavelio.model;

import java.util.List;

public record FftAnalysisResult(
    int sampleRate,
    int windowSize,
    int hopSize,
    double binResolutionHz,
    double[] frequencyAxisHz,
    List<FftChannelSpectrum> channels
) {}

