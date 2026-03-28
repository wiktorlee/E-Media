package org.example.wavelio.service;

import org.example.wavelio.model.WavMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WavParseServiceTest {

    private final WavParseService service = new WavParseService();

    @Test
    void parsesResourceWav() throws Exception {
        Path wav = Path.of(WavParseServiceTest.class.getResource("/minimal.wav").toURI());
        WavMetadata m = service.parse(wav);
        assertEquals(8000, m.sampleRate());
        assertEquals(1, m.numChannels());
        assertEquals(16, m.bitsPerSample());
        assertEquals(2, m.totalSamples());
        assertEquals(2 / 8000.0, m.durationSeconds(), 1e-9);
    }

    @Test
    void rejectsNonRiff(@TempDir Path dir) throws IOException {
        Path bad = dir.resolve("bad.bin");
        Files.writeString(bad, "NOTAWAVFILE");
        assertThrows(WavFormatException.class, () -> service.parse(bad));
    }

    @Test
    void generatedWavMatchesExpected(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("gen.wav");
        writePcmWav(wav, 48000, 2, 16, 100);
        WavMetadata m = service.parse(wav);
        assertEquals(48000, m.sampleRate());
        assertEquals(2, m.numChannels());
        assertEquals(16, m.bitsPerSample());
        assertEquals(100, m.totalSamples());
        assertEquals(100 / 48000.0, m.durationSeconds(), 1e-9);
    }

    private static void writePcmWav(Path path, int sampleRate, int channels, int bitsPerSample, int numFrames)
        throws IOException {
        int bytesPerFrame = channels * (bitsPerSample / 8);
        int dataSize = numFrames * bytesPerFrame;
        int pad = dataSize & 1;
        int fileSize = 12 + 24 + 8 + dataSize + pad;
        int riffChunkSize = fileSize - 8;

        ByteBuffer out = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952);
        out.putInt(riffChunkSize);
        out.putInt(0x45564157);

        out.putInt(0x20746d66);
        out.putInt(16);
        out.putShort((short) 1);
        out.putShort((short) channels);
        out.putInt(sampleRate);
        out.putInt(sampleRate * bytesPerFrame);
        out.putShort((short) bytesPerFrame);
        out.putShort((short) bitsPerSample);

        out.putInt(0x61746164);
        out.putInt(dataSize);
        for (int i = 0; i < dataSize; i++) {
            out.put((byte) 0);
        }
        if (pad == 1) {
            out.put((byte) 0);
        }

        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(out.array());
        }
    }
}
