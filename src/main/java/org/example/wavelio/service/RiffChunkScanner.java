package org.example.wavelio.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minimal low-level RIFF/WAVE chunk scanner.
 * Scans only the top-level RIFF(WAVE) chunk list, preserving offsets/sizes/padding.
 */
public final class RiffChunkScanner {

    private static final int RIFF_LE = 0x46464952; // 'RIFF' in little-endian int read
    private static final int WAVE_LE = 0x45564157; // 'WAVE'
    private static final int LIST_LE = 0x5453494c; // 'LIST'

    public ScanResult scanWave(Path path) throws IOException, WavFormatException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            if (fileSize < 12) {
                throw new WavFormatException("Plik jest zbyt mały, aby był poprawnym RIFF/WAVE.");
            }

            ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            if (!readFully(ch, head, 0)) {
                throw new WavFormatException("Nie udało się odczytać nagłówka RIFF.");
            }
            head.flip();
            int riff = head.getInt();
            if (riff != RIFF_LE) {
                throw new WavFormatException("Oczekiwano chunku RIFF.");
            }
            long riffSize = Integer.toUnsignedLong(head.getInt());
            int wave = head.getInt();
            if (wave != WAVE_LE) {
                throw new WavFormatException("Oczekiwano formatu WAVE.");
            }

            // RIFF chunk size is fileSize - 8 by spec; tolerate slight mismatches but cap scanning to file.
            long riffDataStart = 12;
            long riffDataEndExclusive = Math.min(fileSize, 8L + riffSize);

            List<Chunk> chunks = new ArrayList<>();
            long pos = riffDataStart;
            ByteBuffer chunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            while (pos + 8 <= riffDataEndExclusive) {
                chunkHeader.clear();
                if (!readFully(ch, chunkHeader, pos)) {
                    break;
                }
                chunkHeader.flip();
                int id = chunkHeader.getInt();
                long size = Integer.toUnsignedLong(chunkHeader.getInt());
                long dataOffset = pos + 8;
                long afterData = dataOffset + size;
                if (afterData > fileSize) {
                    throw new WavFormatException("Nieprawidłowy rozmiar chunku względem pliku.");
                }
                int pad = ((size & 1L) == 1L) ? 1 : 0;
                long paddedSize = size + pad;

                Optional<String> listType = Optional.empty();
                if (id == LIST_LE && size >= 4) {
                    ByteBuffer typeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    if (readFully(ch, typeBuf, dataOffset)) {
                        typeBuf.flip();
                        byte[] t = new byte[4];
                        typeBuf.get(t);
                        listType = Optional.of(new String(t, StandardCharsets.ISO_8859_1));
                    }
                }
                chunks.add(new Chunk(id, size, dataOffset, paddedSize, listType));
                pos = dataOffset + paddedSize;
            }

            return new ScanResult(path, fileSize, riffSize, chunks);
        }
    }

    public static String fourCC(int idLeInt) {
        byte[] b = new byte[4];
        b[0] = (byte) (idLeInt & 0xFF);
        b[1] = (byte) ((idLeInt >>> 8) & 0xFF);
        b[2] = (byte) ((idLeInt >>> 16) & 0xFF);
        b[3] = (byte) ((idLeInt >>> 24) & 0xFF);
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    private boolean readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, position);
            if (n <= 0) {
                return false;
            }
            position += n;
        }
        return true;
    }

    public record Chunk(
        int idLeInt,
        long size,
        long dataOffset,
        long paddedSize,
        Optional<String> listType
    ) {
        public String idFourCC() {
            return fourCC(idLeInt);
        }
    }

    public record ScanResult(
        Path path,
        long fileSize,
        long riffSize,
        List<Chunk> chunks
    ) {
        public Optional<Chunk> firstChunk(String fourcc) {
            if (fourcc == null || fourcc.length() != 4) return Optional.empty();
            for (Chunk c : chunks) {
                if (fourcc.equals(c.idFourCC())) {
                    return Optional.of(c);
                }
            }
            return Optional.empty();
        }

        public List<Chunk> listChunksOfType(String typeFourcc) {
            List<Chunk> out = new ArrayList<>();
            for (Chunk c : chunks) {
                if ("LIST".equals(c.idFourCC()) && c.listType().isPresent() && c.listType().get().equals(typeFourcc)) {
                    out.add(c);
                }
            }
            return out;
        }
    }
}

