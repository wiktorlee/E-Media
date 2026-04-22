package org.example.wavelio.service;

import org.example.wavelio.model.SpectrogramResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StftServiceTest {

    private final StftService service = new StftService();

    @Test
    void returnsExpectedShapeAndAxes() {
        int sampleRate = 8000;
        int windowSize = 256;
        int hopSize = 128;
        short[] mono = new short[1024];

        SpectrogramResult result = service.analyzeMonoFromPcm16(
            new short[][] { mono },
            sampleRate,
            windowSize,
            hopSize,
            WindowType.HANN
        );

        assertEquals(7, result.magnitudesDb().length);
        assertEquals(129, result.frequencyAxisHz().length);
        assertEquals(7, result.timeAxisSeconds().length);
        assertEquals(0.0, result.frequencyAxisHz()[0], 1e-9);
        assertEquals(sampleRate / 2.0, result.frequencyAxisHz()[result.frequencyAxisHz().length - 1], 1e-9);
    }

    @Test
    void findsPeakNearToneFrequency() {
        int sampleRate = 8000;
        int windowSize = 512;
        int hopSize = 256;
        short[] mono = new short[4096];
        for (int i = 0; i < mono.length; i++) {
            double value = Math.sin((2.0 * Math.PI * 1000.0 * i) / sampleRate);
            mono[i] = (short) Math.round(value * 28000.0);
        }

        SpectrogramResult result = service.analyzeMonoFromPcm16(
            new short[][] { mono },
            sampleRate,
            windowSize,
            hopSize,
            WindowType.HANN
        );

        double[] avgBins = averageAcrossFrames(result.magnitudesDb());
        int peak = indexOfMax(avgBins);
        double peakHz = result.frequencyAxisHz()[peak];
        double binResolution = sampleRate / (double) windowSize;
        assertTrue(Math.abs(peakHz - 1000.0) <= binResolution * 2.0);
    }

    @Test
    void hammingAndHannProduceDifferentSpectra() {
        int sampleRate = 8000;
        int windowSize = 256;
        int hopSize = 128;
        short[] mono = new short[1024];
        for (int i = 0; i < mono.length; i++) {
            double value = Math.sin((2.0 * Math.PI * 700.0 * i) / sampleRate);
            mono[i] = (short) Math.round(value * 24000.0);
        }

        SpectrogramResult hann = service.analyzeMonoFromPcm16(
            new short[][] { mono }, sampleRate, windowSize, hopSize, WindowType.HANN);
        SpectrogramResult hamming = service.analyzeMonoFromPcm16(
            new short[][] { mono }, sampleRate, windowSize, hopSize, WindowType.HAMMING);

        double hannPeak = maxValue(averageAcrossFrames(hann.magnitudesDb()));
        double hammingPeak = maxValue(averageAcrossFrames(hamming.magnitudesDb()));
        assertTrue(Math.abs(hannPeak - hammingPeak) > 1e-6);
    }

    private static double[] averageAcrossFrames(double[][] matrix) {
        int frames = matrix.length;
        int bins = matrix[0].length;
        double[] out = new double[bins];
        for (double[] frame : matrix) {
            for (int i = 0; i < bins; i++) {
                out[i] += frame[i];
            }
        }
        for (int i = 0; i < bins; i++) {
            out[i] /= frames;
        }
        return out;
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

    private static double maxValue(double[] values) {
        return values[indexOfMax(values)];
    }
}

