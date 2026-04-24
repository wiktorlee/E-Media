package org.example.wavelio.service;

import org.example.wavelio.model.XmpMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmpChunkServiceTest {

    @Test
    void readsXmpChunkWhenPresent(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("with-xmp.wav");
        String xml = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description xmlns:dc="http://purl.org/dc/elements/1.1/" dc:title="Demo"/>
              </rdf:RDF>
            </x:xmpmeta>
            """;
        writePcmWavWithChunk(wav, "XMP ", xml.getBytes(StandardCharsets.UTF_8));

        XmpChunkService svc = new XmpChunkService();
        Optional<XmpMetadata> out = svc.readXmp(wav);

        assertTrue(out.isPresent());
        assertEquals("XMP ", out.get().chunkId());
        assertTrue(out.get().xml().contains("x:xmpmeta"));
        assertTrue(out.get().xml().contains("dc:title=\"Demo\""));
    }

    @Test
    void returnsEmptyWhenXmpMissing(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("without-xmp.wav");
        writePlainPcmWav(wav, 8000, 1, 16, 10);

        XmpChunkService svc = new XmpChunkService();
        Optional<XmpMetadata> out = svc.readXmp(wav);

        assertTrue(out.isEmpty());
    }

    @Test
    void buildChunkRoundTripPreservesXml(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("round-trip.wav");
        XmpChunkService svc = new XmpChunkService();
        XmpMetadata input = new XmpMetadata("XMP ", "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><a>1</a></x:xmpmeta>");
        byte[] xmpChunk = svc.buildXmpChunk(input);

        writePcmWavWithChunk(wav, "XMP ", extractChunkPayload(xmpChunk));
        Optional<XmpMetadata> out = svc.readXmp(wav);

        assertTrue(out.isPresent());
        assertEquals("XMP ", out.get().chunkId());
        assertTrue(out.get().xml().contains("<a>1</a>"));
    }

    private static void writePcmWavWithChunk(Path path, String fourcc, byte[] chunkPayload) throws IOException {
        byte[] fmtChunk = buildFmtChunk(8000, 1, 16);
        byte[] customChunk = buildChunk(fourcc, chunkPayload);
        byte[] dataChunk = buildChunk("data", new byte[40]);

        byte[] payload = concat(
            "WAVE".getBytes(StandardCharsets.ISO_8859_1),
            fmtChunk,
            customChunk,
            dataChunk
        );
        ByteBuffer out = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952);
        out.putInt(payload.length);
        out.put(payload);
        Files.write(path, out.array());
    }

    private static void writePlainPcmWav(Path path, int sampleRate, int channels, int bitsPerSample, int frames)
        throws IOException {
        int bytesPerFrame = channels * (bitsPerSample / 8);
        byte[] fmtChunk = buildFmtChunk(sampleRate, channels, bitsPerSample);
        byte[] dataChunk = buildChunk("data", new byte[frames * bytesPerFrame]);
        byte[] payload = concat(
            "WAVE".getBytes(StandardCharsets.ISO_8859_1),
            fmtChunk,
            dataChunk
        );
        ByteBuffer out = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952);
        out.putInt(payload.length);
        out.put(payload);
        Files.write(path, out.array());
    }

    private static byte[] buildFmtChunk(int sampleRate, int channels, int bitsPerSample) {
        int bytesPerFrame = channels * (bitsPerSample / 8);
        ByteBuffer b = ByteBuffer.allocate(8 + 16).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x20746d66);
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) channels);
        b.putInt(sampleRate);
        b.putInt(sampleRate * bytesPerFrame);
        b.putShort((short) bytesPerFrame);
        b.putShort((short) bitsPerSample);
        return b.array();
    }

    private static byte[] buildChunk(String fourcc, byte[] payload) {
        int size = payload.length;
        int pad = size & 1;
        ByteBuffer b = ByteBuffer.allocate(8 + size + pad).order(ByteOrder.LITTLE_ENDIAN);
        b.put(fourcc.getBytes(StandardCharsets.ISO_8859_1));
        b.putInt(size);
        b.put(payload);
        if (pad == 1) {
            b.put((byte) 0);
        }
        return b.array();
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    private static byte[] extractChunkPayload(byte[] fullChunk) {
        int size = ByteBuffer.wrap(fullChunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] out = new byte[size];
        System.arraycopy(fullChunk, 8, out, 0, size);
        return out;
    }
}

