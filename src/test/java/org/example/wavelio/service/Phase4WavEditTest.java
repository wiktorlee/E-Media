package org.example.wavelio.service;

import org.example.wavelio.model.InfoMetadata;
import org.example.wavelio.model.WavMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase4WavEditTest {

    @Test
    void riffScannerFindsListInfo(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("with-info.wav");
        writePcmWavWithInfoAndJunk(wav, 8000, 1, 16, 10,
            new InfoMetadata(java.util.Map.of("INAM", "Song", "IART", "Artist")));

        RiffChunkScanner.ScanResult scan = new RiffChunkScanner().scanWave(wav);
        assertTrue(scan.firstChunk("fmt ").isPresent());
        assertTrue(scan.firstChunk("data").isPresent());
        assertTrue(!scan.listChunksOfType("INFO").isEmpty());
    }

    @Test
    void infoChunkServiceReadsAndBuilds(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("with-info.wav");
        InfoMetadata in = new InfoMetadata();
        in.put("INAM", "Hello");
        in.put("ICMT", "World");
        writePcmWavWithInfoAndJunk(wav, 44100, 2, 16, 5, in);

        InfoChunkService svc = new InfoChunkService();
        Optional<InfoMetadata> out = svc.readInfo(wav);
        assertTrue(out.isPresent());
        assertEquals("Hello", out.get().get("INAM").orElse(""));
        assertEquals("World", out.get().get("ICMT").orElse(""));
    }

    @Test
    void wavEditCropProducesShorterData(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("src.wav");
        writePcmWavWithInfoAndJunk(wav, 1000, 1, 16, 100, new InfoMetadata());

        Path out = dir.resolve("cropped.wav");
        WavEditService edit = new WavEditService();
        edit.saveEdited(
            wav,
            out,
            Optional.of(new WavEditService.CropRangeMs(0, 50)), // 0.05s @ 1000Hz => 50 frames
            true,
            Optional.empty()
        );

        WavMetadata m = new WavParseService().parse(out);
        assertEquals(50, m.totalSamples());
    }

    @Test
    void wavEditAnonymizeDropsAncillaryButKeepsInfo(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("src.wav");
        InfoMetadata info = new InfoMetadata();
        info.put("INAM", "KeepMe");
        writePcmWavWithInfoAndJunk(wav, 8000, 1, 16, 10, info);

        Path out = dir.resolve("anon.wav");
        WavEditService edit = new WavEditService();
        edit.saveEdited(wav, out, Optional.empty(), true, Optional.empty());

        RiffChunkScanner.ScanResult scan = new RiffChunkScanner().scanWave(out);
        assertTrue(scan.firstChunk("fmt ").isPresent());
        assertTrue(scan.firstChunk("data").isPresent());
        assertTrue(!scan.listChunksOfType("INFO").isEmpty());
        // should not keep JUNK
        assertTrue(scan.firstChunk("JUNK").isEmpty());
    }

    private static void writePcmWavWithInfoAndJunk(
        Path path,
        int sampleRate,
        int channels,
        int bitsPerSample,
        int numFrames,
        InfoMetadata info
    ) throws IOException {
        int bytesPerFrame = channels * (bitsPerSample / 8);
        int dataSize = numFrames * bytesPerFrame;
        byte[] dataPayload = new byte[dataSize];

        byte[] fmtChunk = buildFmtChunk(sampleRate, channels, bitsPerSample);
        byte[] dataChunk = buildDataChunk(dataPayload);
        byte[] junkChunk = buildArbitraryChunk("JUNK", new byte[]{1, 2, 3}); // odd => pad
        byte[] infoChunk = new InfoChunkService().buildInfoListChunk(info);

        byte[] payload = concat(
            "WAVE".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
            fmtChunk,
            junkChunk,
            infoChunk,
            dataChunk
        );
        int riffSize = payload.length;

        ByteBuffer out = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952); // RIFF
        out.putInt(riffSize);
        out.put(payload);
        Files.write(path, out.array());
    }

    private static byte[] buildFmtChunk(int sampleRate, int channels, int bitsPerSample) {
        int bytesPerFrame = channels * (bitsPerSample / 8);
        ByteBuffer b = ByteBuffer.allocate(8 + 16).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x20746d66); // fmt 
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) channels);
        b.putInt(sampleRate);
        b.putInt(sampleRate * bytesPerFrame);
        b.putShort((short) bytesPerFrame);
        b.putShort((short) bitsPerSample);
        return b.array();
    }

    private static byte[] buildDataChunk(byte[] payload) {
        int size = payload.length;
        int pad = size & 1;
        ByteBuffer b = ByteBuffer.allocate(8 + size + pad).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x61746164); // data
        b.putInt(size);
        b.put(payload);
        if (pad == 1) b.put((byte) 0);
        return b.array();
    }

    private static byte[] buildArbitraryChunk(String fourcc, byte[] payload) {
        int size = payload.length;
        int pad = size & 1;
        ByteBuffer b = ByteBuffer.allocate(8 + size + pad).order(ByteOrder.LITTLE_ENDIAN);
        b.put(fourcc.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        b.putInt(size);
        b.put(payload);
        if (pad == 1) b.put((byte) 0);
        return b.array();
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }
}

