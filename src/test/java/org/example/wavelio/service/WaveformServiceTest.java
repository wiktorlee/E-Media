package org.example.wavelio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveformServiceTest {

    private final WaveformService service = new WaveformService();

    @Test
    void returnsEmptyForNoChannels() {
        double[][] out = service.buildLinearPrecomputed(new short[0][0], 100);
        assertEquals(0, out.length);
    }

    @Test
    void preservesChannelCountAndTargetLength() {
        short[][] in = new short[][]{
            new short[]{0, 1, 2, 3, 4, 5},
            new short[]{5, 4, 3, 2, 1, 0}
        };
        double[][] out = service.buildLinearPrecomputed(in, 4);
        assertEquals(2, out.length);
        assertEquals(4, out[0].length);
        assertEquals(4, out[1].length);
    }

    @Test
    void usesPeakSampleInBucket() {
        short[][] in = new short[][]{
            new short[]{0, 10, -30000, 20, 30, 40, 50, 60}
        };
        double[][] out = service.buildLinearPrecomputed(in, 2);
        assertTrue(out[0][0] < -0.8);
    }

    @Test
    void handlesLongInputWithoutOverflow() {
        int n = 1_200_000;
        short[] ch = new short[n];
        for (int i = 0; i < n; i++) {
            ch[i] = (short) (i % 32768);
        }
        double[][] out = service.buildLinearPrecomputed(new short[][]{ch, ch}, 2400);
        assertEquals(2, out.length);
        assertEquals(2400, out[0].length);
        assertEquals(2400, out[1].length);
    }
}

