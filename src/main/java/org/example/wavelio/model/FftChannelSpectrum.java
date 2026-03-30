package org.example.wavelio.model;

public record FftChannelSpectrum(
    int channelIndex,
    double[] magnitudesDb
) {}

