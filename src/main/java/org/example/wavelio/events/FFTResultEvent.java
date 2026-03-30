package org.example.wavelio.events;

import org.example.wavelio.model.FftAnalysisResult;

public record FFTResultEvent(FftAnalysisResult spectrumData) {}
