package org.example.wavelio.service;

import org.example.wavelio.model.InfoMetadata;
import org.example.wavelio.model.XmpMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Writes edited WAV files (crop, anonymize, update LIST/INFO).
 *
 * Implementation note: when edits are required we rebuild a minimal RIFF/WAVE containing:
 * - fmt  (copied verbatim from source)
 * - LIST/INFO (optional; preserved or replaced)
 * - data (full or cropped)
 *
 * This matches Phase 4 requirements (anonimizacja usuwa ancillary chunks).
 */
public final class WavEditService {

    private static final int CHUNK_FMT_LE = 0x20746d66;  // 'fmt '
    private static final int CHUNK_DATA_LE = 0x61746164; // 'data'

    public void saveEdited(
        Path sourceWav,
        Path destinationWav,
        Optional<CropRangeMs> cropRange,
        boolean anonymize,
        Optional<InfoMetadata> infoOverride,
        Optional<XmpMetadata> xmpOverride
    ) throws IOException, WavFormatException {
        if (sourceWav == null || destinationWav == null) {
            throw new IllegalArgumentException("Paths cannot be null");
        }
        if (!Files.exists(sourceWav)) {
            throw new IOException("Źródłowy plik nie istnieje: " + sourceWav);
        }

        boolean requiresRewrite = anonymize || cropRange.isPresent() || infoOverride.isPresent() || xmpOverride.isPresent();
        if (!requiresRewrite) {
            copyPossiblyOverwriting(sourceWav, destinationWav);
            return;
        }

        Path actualDest = destinationWav;
        boolean overwrite = Files.exists(destinationWav) && Files.isSameFile(sourceWav, destinationWav);
        if (overwrite) {
            Path parent = destinationWav.getParent();
            if (parent == null) parent = Path.of(".");
            actualDest = Files.createTempFile(parent, "wavelio-edit-", ".wav");
        } else {
            Path parent = destinationWav.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }

        writeRebuiltMinimal(sourceWav, actualDest, cropRange, infoOverride, xmpOverride, anonymize);

        if (overwrite) {
            try {
                Files.move(actualDest, destinationWav, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(actualDest, destinationWav, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void copyPossiblyOverwriting(Path src, Path dst) throws IOException {
        if (Files.exists(dst) && Files.isSameFile(src, dst)) {
            return;
        }
        Path parent = dst.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeRebuiltMinimal(
        Path sourceWav,
        Path destinationWav,
        Optional<CropRangeMs> cropRange,
        Optional<InfoMetadata> infoOverride,
        Optional<XmpMetadata> xmpOverride,
        boolean anonymize
    ) throws IOException, WavFormatException {
        RiffChunkScanner scanner = new RiffChunkScanner();
        RiffChunkScanner.ScanResult scan = scanner.scanWave(sourceWav);

        RiffChunkScanner.Chunk fmt = scan.chunks().stream().filter(c -> c.idLeInt() == CHUNK_FMT_LE).findFirst()
            .orElseThrow(() -> new WavFormatException("Brak wymaganego chunku fmt ."));
        RiffChunkScanner.Chunk data = scan.chunks().stream().filter(c -> c.idLeInt() == CHUNK_DATA_LE).findFirst()
            .orElseThrow(() -> new WavFormatException("Brak chunku data."));

        byte[] fmtBytes;
        try (FileChannel ch = FileChannel.open(sourceWav, StandardOpenOption.READ)) {
            fmtBytes = readChunkWithHeader(ch, fmt);
        }

        // Read needed format fields for crop alignment: sampleRate + blockAlign from fmt payload.
        FmtFields fmtFields = readFmtFields(sourceWav, fmt);
        long dataPayloadOffset = data.dataOffset();
        long dataSize = data.size();

        long cropStartByte = 0;
        long cropEndByte = dataSize;
        if (cropRange.isPresent()) {
            CropRangeMs r = cropRange.get();
            long startFrame = (long) Math.floor((r.startMs() / 1000.0) * fmtFields.sampleRate());
            long endFrame = (long) Math.ceil((r.endMs() / 1000.0) * fmtFields.sampleRate());
            if (endFrame < startFrame) {
                long tmp = startFrame;
                startFrame = endFrame;
                endFrame = tmp;
            }
            long maxFrames = dataSize / fmtFields.blockAlign();
            startFrame = Math.max(0, Math.min(maxFrames, startFrame));
            endFrame = Math.max(0, Math.min(maxFrames, endFrame));
            cropStartByte = startFrame * (long) fmtFields.blockAlign();
            cropEndByte = endFrame * (long) fmtFields.blockAlign();
        }
        long croppedDataSize = Math.max(0, cropEndByte - cropStartByte);

        // INFO: preserve existing unless overridden.
        byte[] infoChunkBytes = new byte[0];
        Optional<InfoMetadata> infoToWrite = infoOverride;
        if (infoToWrite.isEmpty()) {
            InfoChunkService infoChunkService = new InfoChunkService();
            Optional<InfoMetadata> existing = infoChunkService.readInfo(sourceWav);
            infoToWrite = existing;
        }
        if (infoToWrite.isPresent() && !infoToWrite.get().isEmpty()) {
            InfoChunkService infoChunkService = new InfoChunkService();
            infoChunkBytes = infoChunkService.buildInfoListChunk(infoToWrite.get());
        }

        // XMP: preserve unless anonymization is requested.
        byte[] xmpChunkBytes = new byte[0];
        if (!anonymize) {
            XmpChunkService xmpChunkService = new XmpChunkService();
            Optional<XmpMetadata> xmpToWrite = xmpOverride;
            if (xmpToWrite.isEmpty()) {
                xmpToWrite = xmpChunkService.readXmp(sourceWav);
            }
            if (xmpToWrite.isPresent() && !xmpToWrite.get().isEmpty()) {
                xmpChunkBytes = xmpChunkService.buildXmpChunk(xmpToWrite.get());
            }
        }

        // Build RIFF/WAVE: header + fmt + (info) + (xmp) + data
        long riffPayloadSize = 4L; // "WAVE"
        riffPayloadSize += fmtBytes.length;
        riffPayloadSize += infoChunkBytes.length;
        riffPayloadSize += xmpChunkBytes.length;
        riffPayloadSize += 8L + croppedDataSize + ((croppedDataSize & 1L) == 1L ? 1L : 0L);

        long riffSize = riffPayloadSize; // size field is bytes after 'RIFF' + size

        try (FileChannel in = FileChannel.open(sourceWav, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(destinationWav,
                 StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(0x46464952); // RIFF
            header.putInt((int) riffSize);
            header.putInt(0x45564157); // WAVE
            header.flip();
            out.write(header);

            out.write(ByteBuffer.wrap(fmtBytes));
            if (infoChunkBytes.length > 0) {
                out.write(ByteBuffer.wrap(infoChunkBytes));
            }
            if (xmpChunkBytes.length > 0) {
                out.write(ByteBuffer.wrap(xmpChunkBytes));
            }

            // data header
            ByteBuffer dataHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            dataHeader.putInt(CHUNK_DATA_LE);
            dataHeader.putInt((int) croppedDataSize);
            dataHeader.flip();
            out.write(dataHeader);

            // copy cropped payload
            long toCopy = croppedDataSize;
            long inPos = dataPayloadOffset + cropStartByte;
            while (toCopy > 0) {
                long transferred = in.transferTo(inPos, toCopy, out);
                if (transferred <= 0) {
                    break;
                }
                inPos += transferred;
                toCopy -= transferred;
            }

            // pad if odd
            if ((croppedDataSize & 1L) == 1L) {
                out.write(ByteBuffer.wrap(new byte[]{0}));
            }
        }
    }

    private static byte[] readChunkWithHeader(FileChannel ch, RiffChunkScanner.Chunk c) throws IOException {
        long total = 8L + c.size() + (((c.size() & 1L) == 1L) ? 1L : 0L);
        if (total > Integer.MAX_VALUE) {
            throw new IOException("Chunk too large: " + total);
        }
        ByteBuffer buf = ByteBuffer.allocate((int) total).order(ByteOrder.LITTLE_ENDIAN);
        // header
        buf.putInt(c.idLeInt());
        buf.putInt((int) c.size());
        // payload
        ByteBuffer payload = ByteBuffer.allocate((int) c.size());
        readFully(ch, payload, c.dataOffset());
        payload.flip();
        buf.put(payload);
        if ((c.size() & 1L) == 1L) {
            buf.put((byte) 0);
        }
        return buf.array();
    }

    private static void readFully(FileChannel ch, ByteBuffer buf, long pos) throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.read(buf, pos);
            if (n < 0) {
                throw new IOException("Unexpected EOF");
            }
            if (n == 0) {
                throw new IOException("Could not read from file");
            }
            pos += n;
        }
    }

    private static FmtFields readFmtFields(Path wav, RiffChunkScanner.Chunk fmt) throws IOException, WavFormatException {
        if (fmt.size() < 16) {
            throw new WavFormatException("Chunk fmt jest zbyt krótki.");
        }
        // Need: sampleRate (offset 4) and blockAlign (offset 12) within PCM fmt layout.
        // Layout: wFormatTag(2), nChannels(2), nSamplesPerSec(4), nAvgBytesPerSec(4), nBlockAlign(2), wBitsPerSample(2)
        ByteBuffer b = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        try (FileChannel ch = FileChannel.open(wav, StandardOpenOption.READ)) {
            readFully(ch, b, fmt.dataOffset());
        }
        b.flip();
        int audioFormat = b.getShort() & 0xFFFF;
        if (audioFormat != 1) {
            throw new WavFormatException("Obsługiwany jest tylko format PCM (1), otrzymano: " + audioFormat);
        }
        b.getShort(); // channels
        int sampleRate = b.getInt();
        b.getInt(); // avg bytes/sec
        int blockAlign = b.getShort() & 0xFFFF;
        if (sampleRate <= 0 || blockAlign <= 0) {
            throw new WavFormatException("Nieprawidłowe pole w chunku fmt.");
        }
        return new FmtFields(sampleRate, blockAlign);
    }

    private record FmtFields(int sampleRate, int blockAlign) {}

    public record CropRangeMs(long startMs, long endMs) {}
}

