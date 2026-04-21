package org.example.wavelio.service;

import org.example.wavelio.model.InfoMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes RIFF LIST/INFO metadata.
 */
public final class InfoChunkService {

    private static final Charset INFO_TEXT = StandardCharsets.ISO_8859_1;

    public Optional<InfoMetadata> readInfo(Path wavPath) throws IOException, WavFormatException {
        RiffChunkScanner scanner = new RiffChunkScanner();
        RiffChunkScanner.ScanResult scan = scanner.scanWave(wavPath);
        List<RiffChunkScanner.Chunk> lists = scan.listChunksOfType("INFO");
        if (lists.isEmpty()) {
            return Optional.empty();
        }
        // Use first LIST/INFO (typical for WAV).
        RiffChunkScanner.Chunk list = lists.get(0);
        long listDataOffset = list.dataOffset();
        long listSize = list.size();
        if (listSize < 4) {
            return Optional.empty();
        }

        // LIST chunk payload: 4 bytes type + subchunks...
        long subStart = listDataOffset + 4;
        long subEndExclusive = listDataOffset + listSize;
        Map<String, String> fields = new LinkedHashMap<>();

        try (FileChannel ch = FileChannel.open(wavPath, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            long pos = subStart;
            while (pos + 8 <= subEndExclusive) {
                header.clear();
                if (!readFully(ch, header, pos)) {
                    break;
                }
                header.flip();
                int id = header.getInt();
                long size = Integer.toUnsignedLong(header.getInt());
                long dataOffset = pos + 8;
                long afterData = dataOffset + size;
                if (afterData > subEndExclusive) {
                    break;
                }

                byte[] data = readBytes(ch, dataOffset, size);
                String value = decodeInfoString(data);
                fields.put(RiffChunkScanner.fourCC(id), value);

                int pad = ((size & 1L) == 1L) ? 1 : 0;
                pos = afterData + pad;
            }
        }

        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new InfoMetadata(fields));
    }

    /**
     * Builds a complete LIST/INFO chunk (including 'LIST' id + size + 'INFO' type).
     */
    public byte[] buildInfoListChunk(InfoMetadata info) {
        if (info == null || info.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        // LIST type
        writeFourCC(payload, "INFO");

        for (Map.Entry<String, String> e : info.fields().entrySet()) {
            String tag = e.getKey();
            String text = e.getValue() == null ? "" : e.getValue();
            byte[] str = encodeInfoString(text);
            int size = str.length;

            writeFourCC(payload, tag);
            writeUInt32LE(payload, size);
            payload.writeBytes(str);
            if ((size & 1) == 1) {
                payload.write(0);
            }
        }

        byte[] listPayload = payload.toByteArray();
        int listSize = listPayload.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFourCC(out, "LIST");
        writeUInt32LE(out, listSize);
        out.writeBytes(listPayload);
        if ((listSize & 1) == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static String decodeInfoString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        int end = bytes.length;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                end = i;
                break;
            }
        }
        return new String(bytes, 0, end, INFO_TEXT).trim();
    }

    private static byte[] encodeInfoString(String value) {
        String v = value == null ? "" : value;
        byte[] s = v.getBytes(INFO_TEXT);
        // INFO strings are typically NUL-terminated.
        byte[] out = new byte[s.length + 1];
        System.arraycopy(s, 0, out, 0, s.length);
        out[out.length - 1] = 0;
        return out;
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

    private static boolean readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, position);
            if (n <= 0) {
                return false;
            }
            position += n;
        }
        return true;
    }

    private static byte[] readBytes(FileChannel ch, long offset, long size) throws IOException {
        if (size <= 0) return new byte[0];
        if (size > Integer.MAX_VALUE) {
            throw new IOException("Chunk too large to read into memory: " + size);
        }
        ByteBuffer buf = ByteBuffer.allocate((int) size);
        long pos = offset;
        while (buf.hasRemaining()) {
            int n = ch.read(buf, pos);
            if (n < 0) break;
            if (n == 0) break;
            pos += n;
        }
        return buf.array();
    }
}

