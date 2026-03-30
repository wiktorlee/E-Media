package org.example.wavelio.service;

import org.example.wavelio.model.FftAnalysisResult;
import org.example.wavelio.model.FftChannelSpectrum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FFTServiceTest {

    private final FFTService fftService = new FFTService();

    @Test
    void detectsPeakAround440Hz() {
        int sampleRate = 44100;
        int length = 4096;
        short[] channel = new short[length];
        for (int i = 0; i < length; i++) {
            double v = Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate);
            channel[i] = (short) Math.round(v * 30000.0);
        }

        FftAnalysisResult result = fftService.analyzePerChannelPcm16(new short[][]{channel}, sampleRate);
        FftChannelSpectrum spectrum = result.channels().get(0);
        int peak = indexOfMax(spectrum.magnitudesDb());
        double peakHz = result.frequencyAxisHz()[peak];
        assertTrue(Math.abs(peakHz - 440.0) <= result.binResolutionHz() * 2.0);
    }

    @Test
    void silenceFallsToDbFloor() {
        int sampleRate = 44100;
        short[] channel = new short[2048];
        FftAnalysisResult result = fftService.analyzePerChannelPcm16(new short[][]{channel}, sampleRate);
        double[] mags = result.channels().get(0).magnitudesDb();
        for (double db : mags) {
            assertEquals(-120.0, db, 1e-9);
        }
    }

    @Test
    void handlesShortInputAndMultipleChannels() {
        int sampleRate = 48000;
        short[] left = new short[123];
        short[] right = new short[123];
        for (int i = 0; i < left.length; i++) {
            left[i] = (short) (i * 10);
            right[i] = (short) (-i * 7);
        }

        FftAnalysisResult result = fftService.analyzePerChannelPcm16(new short[][]{left, right}, sampleRate);
        assertEquals(2, result.channels().size());
        assertEquals(1025, result.frequencyAxisHz().length);
        assertEquals(1025, result.channels().get(0).magnitudesDb().length);
        assertEquals(1025, result.channels().get(1).magnitudesDb().length);
    }

    private static int indexOfMax(double[] values) {
        int idx = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[idx]) {
                idx = i;
            }
        }
        return idx;
    }
}

