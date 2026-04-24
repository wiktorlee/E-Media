package org.example.wavelio.service;

import org.example.wavelio.model.XmpMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

/**
 * Reads XMP metadata stored in WAV RIFF chunks.
 */
public final class XmpChunkService {

    private static final List<String> XMP_CHUNK_IDS = List.of("XMP ", "axml", "iXML");

    public Optional<XmpMetadata> readXmp(Path wavPath) throws IOException, WavFormatException {
        RiffChunkScanner scanner = new RiffChunkScanner();
        RiffChunkScanner.ScanResult scan = scanner.scanWave(wavPath);

        for (String chunkId : XMP_CHUNK_IDS) {
            Optional<RiffChunkScanner.Chunk> maybeChunk = scan.firstChunk(chunkId);
            if (maybeChunk.isEmpty()) {
                continue;
            }
            RiffChunkScanner.Chunk chunk = maybeChunk.get();
            byte[] data = readBytes(wavPath, chunk.dataOffset(), chunk.size());
            String xml = decodePacket(data);
            if (!xml.isBlank()) {
                return Optional.of(new XmpMetadata(chunkId, xml));
            }
        }
        return Optional.empty();
    }

    public byte[] buildXmpChunk(XmpMetadata xmp) {
        if (xmp == null || xmp.isEmpty()) {
            return new byte[0];
        }
        byte[] payload = xmp.xml().getBytes(StandardCharsets.UTF_8);
        int size = payload.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFourCC(out, xmp.chunkId());
        writeUInt32LE(out, size);
        out.writeBytes(payload);
        if ((size & 1) == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] readBytes(Path path, long offset, long size) throws IOException {
        if (size <= 0) {
            return new byte[0];
        }
        if (size > Integer.MAX_VALUE) {
            throw new IOException("Chunk too large to read into memory: " + size);
        }
        ByteBuffer buf = ByteBuffer.allocate((int) size);
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            long pos = offset;
            while (buf.hasRemaining()) {
                int n = ch.read(buf, pos);
                if (n <= 0) {
                    break;
                }
                pos += n;
            }
        }
        return buf.array();
    }

    private static String decodePacket(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        int end = raw.length;
        while (end > 0 && raw[end - 1] == 0) {
            end--;
        }
        return new String(raw, 0, end, StandardCharsets.UTF_8).trim();
    }

    private static void writeFourCC(ByteArrayOutputStream out, String fourcc) {
        byte[] b = fourcc.getBytes(StandardCharsets.ISO_8859_1);
        if (b.length != 4) {
            throw new IllegalArgumentException("FourCC must be 4 bytes: " + fourcc);
        }
        out.writeBytes(b);
    }

    private static void writeUInt32LE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }
}

