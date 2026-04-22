package org.example.wavelio.service;

import org.example.wavelio.model.SpectrogramResult;
import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;

public final class StftService {

    private static final double DB_EPSILON = 1.0e-12;
    private static final double DB_FLOOR = -120.0;

    public SpectrogramResult analyzeMonoFromPcm16(
        short[][] channels,
        int sampleRate,
        int windowSize,
        int hopSize,
        WindowType windowType
    ) {
        validateInput(channels, sampleRate, windowSize, hopSize, windowType);

        int frames = channels[0].length;
        double[] mono = mixToMono(channels, frames);
        int frameCount = computeFrameCount(frames, windowSize, hopSize);
        int bins = (windowSize / 2) + 1;

        double[] window = buildWindow(windowSize, windowType);
        DoubleFFT_1D fft = new DoubleFFT_1D(windowSize);
        double[] packed = new double[2 * windowSize];
        double[][] db = new double[frameCount][bins];
        double[] timeAxis = new double[frameCount];
        double[] freqAxis = buildFrequencyAxis(sampleRate, windowSize);

        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int start = Math.min(frameIndex * hopSize, Math.max(0, frames - windowSize));
            Arrays.fill(packed, 0.0);

            int available = Math.min(windowSize, frames - start);
            for (int i = 0; i < available; i++) {
                packed[i] = mono[start + i] * window[i];
            }
            fft.realForwardFull(packed);

            for (int k = 0; k < bins; k++) {
                double re = packed[2 * k];
                double im = packed[(2 * k) + 1];
                double mag = Math.sqrt((re * re) + (im * im));
                double value = 20.0 * Math.log10(Math.max(mag, DB_EPSILON));
                db[frameIndex][k] = Math.max(value, DB_FLOOR);
            }

            timeAxis[frameIndex] = start / (double) sampleRate;
        }

        return new SpectrogramResult(
            sampleRate,
            windowSize,
            hopSize,
            timeAxis,
            freqAxis,
            db
        );
    }

    private static void validateInput(short[][] channels, int sampleRate, int windowSize, int hopSize, WindowType windowType) {
        if (channels == null || channels.length == 0) {
            throw new IllegalArgumentException("Brak kanałów do analizy.");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate musi być dodatnie.");
        }
        if (windowType == null) {
            throw new IllegalArgumentException("windowType nie może być null.");
        }
        if (windowSize <= 0 || (windowSize & (windowSize - 1)) != 0) {
            throw new IllegalArgumentException("windowSize musi być dodatnią potęgą 2.");
        }
        if (hopSize <= 0 || hopSize > windowSize) {
            throw new IllegalArgumentException("hopSize musi być z zakresu 1..windowSize.");
        }
        int expectedLength = channels[0].length;
        if (expectedLength == 0) {
            throw new IllegalArgumentException("Kanał nie może być pusty.");
        }
        for (short[] channel : channels) {
            if (channel == null || channel.length != expectedLength) {
                throw new IllegalArgumentException("Wszystkie kanały muszą mieć tę samą długość.");
            }
        }
    }

    private static int computeFrameCount(int sampleCount, int windowSize, int hopSize) {
        if (sampleCount <= windowSize) {
            return 1;
        }
        return ((sampleCount - windowSize) / hopSize) + 1;
    }

    private static double[] mixToMono(short[][] channels, int frames) {
        double[] mono = new double[frames];
        for (int i = 0; i < frames; i++) {
            double sum = 0.0;
            for (short[] channel : channels) {
                sum += channel[i] / 32768.0;
            }
            mono[i] = sum / channels.length;
        }
        return mono;
    }

    private static double[] buildFrequencyAxis(int sampleRate, int windowSize) {
        int bins = (windowSize / 2) + 1;
        double[] axis = new double[bins];
        double step = sampleRate / (double) windowSize;
        for (int i = 0; i < bins; i++) {
            axis[i] = i * step;
        }
        return axis;
    }

    private static double[] buildWindow(int size, WindowType type) {
        double[] window = new double[size];
        if (size == 1) {
            window[0] = 1.0;
            return window;
        }
        for (int i = 0; i < size; i++) {
            double ratio = (2.0 * Math.PI * i) / (size - 1);
            if (type == WindowType.HAMMING) {
                window[i] = 0.54 - (0.46 * Math.cos(ratio));
            } else {
                window[i] = 0.5 * (1.0 - Math.cos(ratio));
            }
        }
        return window;
    }
}

