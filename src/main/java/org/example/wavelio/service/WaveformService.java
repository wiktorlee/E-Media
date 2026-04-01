package org.example.wavelio.service;

public final class WaveformService {

    public double[][] buildLinearPrecomputed(short[][] channels, int points) {
        if (channels == null || channels.length == 0 || channels[0].length == 0) {
            return new double[0][0];
        }
        int channelCount = channels.length;
        int sourceLength = channels[0].length;
        int targetLength = Math.min(points, sourceLength);
        double[][] out = new double[channelCount][targetLength];
        for (int ch = 0; ch < channelCount; ch++) {
            short[] source = channels[ch];
            for (int i = 0; i < targetLength; i++) {
                int start = (int) (((long) i * sourceLength) / targetLength);
                int end = (int) (((long) (i + 1) * sourceLength) / targetLength);
                if (end <= start) {
                    end = Math.min(sourceLength, start + 1);
                }
                short chosen = 0;
                int maxAbs = -1;
                for (int j = start; j < end; j++) {
                    int abs = Math.abs(source[j]);
                    if (abs > maxAbs) {
                        maxAbs = abs;
                        chosen = source[j];
                    }
                }
                out[ch][i] = chosen / 32768.0;
            }
        }
        return out;
    }
}

