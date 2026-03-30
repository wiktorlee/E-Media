package org.example.wavelio.service;

import org.example.wavelio.model.FftAnalysisResult;
import org.example.wavelio.model.FftChannelSpectrum;

import java.util.ArrayList;
import java.util.List;

public final class FFTService {

    private static final int DEFAULT_WINDOW_SIZE = 2048;
    private static final int MAX_WINDOWS = 512;
    private static final double DB_EPSILON = 1.0e-12;
    private static final double DB_FLOOR = -120.0;

    private final int windowSize;
    private final int hopSize;
    private final double[] hannWindow;

    public FFTService() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_WINDOW_SIZE / 2);
    }

    public FFTService(int windowSize, int hopSize) {
        if (windowSize <= 0 || (windowSize & (windowSize - 1)) != 0) {
            throw new IllegalArgumentException("windowSize musi być dodatnią potęgą 2.");
        }
        if (hopSize <= 0 || hopSize > windowSize) {
            throw new IllegalArgumentException("hopSize musi być z zakresu 1..windowSize.");
        }
        this.windowSize = windowSize;
        this.hopSize = hopSize;
        this.hannWindow = buildHannWindow(windowSize);
    }

    public FftAnalysisResult analyzePerChannelPcm16(short[][] channels, int sampleRate) {
        return analyzePerChannelPcm16(channels, sampleRate, null);
    }

    public FftAnalysisResult analyzePerChannelPcm16(
        short[][] channels,
        int sampleRate,
        ProgressListener progressListener
    ) {
        if (channels == null || channels.length == 0) {
            throw new IllegalArgumentException("Brak kanałów do analizy.");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate musi być dodatnie.");
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

        double[] freqAxis = buildFrequencyAxis(sampleRate, windowSize);
        List<FftChannelSpectrum> resultChannels = new ArrayList<>(channels.length);
        for (int i = 0; i < channels.length; i++) {
            double[] db = analyzeSingleChannelToDb(channels[i]);
            resultChannels.add(new FftChannelSpectrum(i, db));
            if (progressListener != null) {
                progressListener.onProgress((i + 1) / (double) channels.length);
            }
        }

        return new FftAnalysisResult(
            sampleRate,
            windowSize,
            hopSize,
            sampleRate / (double) windowSize,
            freqAxis,
            resultChannels
        );
    }

    private double[] analyzeSingleChannelToDb(short[] samples) {
        int bins = windowSize / 2 + 1;
        double[] magnitudeAccumulator = new double[bins];
        int windowsPossible = samples.length <= windowSize
            ? 1
            : ((samples.length - windowSize) / hopSize) + 1;
        int windowsToUse = Math.min(windowsPossible, MAX_WINDOWS);
        if (windowsToUse <= 0) {
            windowsToUse = 1;
        }
        double spacing = windowsToUse == 1
            ? 0.0
            : (windowsPossible - 1) / (double) (windowsToUse - 1);

        int windows = 0;
        int previousStart = -1;
        for (int w = 0; w < windowsToUse; w++) {
            int windowIndex = (int) Math.round(w * spacing);
            int start = Math.min(windowIndex * hopSize, Math.max(0, samples.length - windowSize));
            if (start == previousStart && w > 0) {
                continue;
            }
            previousStart = start;
            double[] real = new double[windowSize];
            int available = Math.min(windowSize, samples.length - start);
            for (int i = 0; i < available; i++) {
                double normalized = samples[start + i] / 32768.0;
                real[i] = normalized * hannWindow[i];
            }
            double[] imag = new double[windowSize];
            fftInPlace(real, imag);
            for (int k = 0; k < bins; k++) {
                double re = real[k];
                double im = imag[k];
                double mag = Math.sqrt(re * re + im * im);
                magnitudeAccumulator[k] += mag;
            }
            windows++;
        }

        if (windows == 0) {
            windows = 1;
        }
        double[] db = new double[bins];
        for (int i = 0; i < bins; i++) {
            double avgMag = magnitudeAccumulator[i] / windows;
            double val = 20.0 * Math.log10(Math.max(avgMag, DB_EPSILON));
            db[i] = Math.max(val, DB_FLOOR);
        }
        return db;
    }

    private static double[] buildHannWindow(int n) {
        double[] window = new double[n];
        if (n == 1) {
            window[0] = 1.0;
            return window;
        }
        for (int i = 0; i < n; i++) {
            window[i] = 0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (n - 1)));
        }
        return window;
    }

    private static double[] buildFrequencyAxis(int sampleRate, int windowSize) {
        int bins = windowSize / 2 + 1;
        double[] axis = new double[bins];
        double binHz = sampleRate / (double) windowSize;
        for (int i = 0; i < bins; i++) {
            axis[i] = i * binHz;
        }
        return axis;
    }

    private static void fftInPlace(double[] real, double[] imag) {
        int n = real.length;

        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
            if (i < j) {
                double tmpRe = real[i];
                real[i] = real[j];
                real[j] = tmpRe;
                double tmpIm = imag[i];
                imag[i] = imag[j];
                imag[j] = tmpIm;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            double wLenRe = Math.cos(angle);
            double wLenIm = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double wRe = 1.0;
                double wIm = 0.0;
                int halfLen = len >> 1;
                for (int k = 0; k < halfLen; k++) {
                    int u = i + k;
                    int v = u + halfLen;
                    double vRe = real[v] * wRe - imag[v] * wIm;
                    double vIm = real[v] * wIm + imag[v] * wRe;
                    real[v] = real[u] - vRe;
                    imag[v] = imag[u] - vIm;
                    real[u] += vRe;
                    imag[u] += vIm;

                    double nextWRe = wRe * wLenRe - wIm * wLenIm;
                    double nextWIm = wRe * wLenIm + wIm * wLenRe;
                    wRe = nextWRe;
                    wIm = nextWIm;
                }
            }
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(double value);
    }
}

