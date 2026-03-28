package org.example.wavelio.service;

import org.example.wavelio.model.WavMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class WavParseService {

    private static final int RIFF_LE = 0x46464952;
    private static final int WAVE_LE = 0x45564157;
    private static final int CHUNK_FMT = 0x20746d66;
    private static final int CHUNK_DATA = 0x61746164;

    private static final int PCM_FORMAT = 1;

    public WavMetadata parse(Path path) throws IOException, WavFormatException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < 44) {
                throw new WavFormatException("Plik jest zbyt mały, aby był poprawnym WAV.");
            }

            ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            int read = channel.read(head, 0);
            if (read < 12) {
                throw new WavFormatException("Nie udało się odczytać nagłówka RIFF.");
            }
            head.flip();
            if (head.getInt() != RIFF_LE) {
                throw new WavFormatException("Oczekiwano chunku RIFF.");
            }
            head.getInt();
            if (head.getInt() != WAVE_LE) {
                throw new WavFormatException("Oczekiwano formatu WAVE.");
            }

            long position = 12;
            Integer numChannels = null;
            Integer sampleRate = null;
            Integer bitsPerSample = null;
            Integer blockAlign = null;
            Long dataSize = null;

            ByteBuffer chunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);

            while (position + 8 <= fileSize) {
                chunkHeader.clear();
                int n = channel.read(chunkHeader, position);
                if (n < 8) {
                    break;
                }
                chunkHeader.flip();
                int chunkId = chunkHeader.getInt();
                long chunkSize = Integer.toUnsignedLong(chunkHeader.getInt());
                long dataStart = position + 8;
                long afterChunk = dataStart + chunkSize;
                int pad = ((chunkSize & 1L) == 1L) ? 1 : 0;
                long nextPosition = afterChunk + pad;

                if (afterChunk > fileSize) {
                    throw new WavFormatException("Nieprawidłowy rozmiar chunku względem pliku.");
                }

                if (chunkId == CHUNK_FMT) {
                    if (chunkSize < 16) {
                        throw new WavFormatException("Chunk fmt jest zbyt krótki.");
                    }
                    int fmtRead = (int) Math.min(chunkSize, 40L);
                    ByteBuffer fmt = ByteBuffer.allocate(fmtRead).order(ByteOrder.LITTLE_ENDIAN);
                    channel.read(fmt, dataStart);
                    fmt.flip();
                    int audioFormat = fmt.getShort() & 0xFFFF;
                    if (audioFormat != PCM_FORMAT) {
                        throw new WavFormatException("Obsługiwany jest tylko format PCM (1), otrzymano: " + audioFormat);
                    }
                    numChannels = fmt.getShort() & 0xFFFF;
                    sampleRate = fmt.getInt();
                    fmt.getInt();
                    blockAlign = fmt.getShort() & 0xFFFF;
                    bitsPerSample = fmt.getShort() & 0xFFFF;
                    if (numChannels == 0 || sampleRate <= 0 || bitsPerSample == 0 || blockAlign == 0) {
                        throw new WavFormatException("Nieprawidłowe pole w chunku fmt.");
                    }
                } else if (chunkId == CHUNK_DATA) {
                    dataSize = (long) chunkSize;
                }

                position = nextPosition;
            }

            if (numChannels == null || sampleRate == null || bitsPerSample == null || blockAlign == null) {
                throw new WavFormatException("Brak wymaganego chunku fmt .");
            }
            if (dataSize == null) {
                throw new WavFormatException("Brak chunku data.");
            }

            int bytesPerSampleAllChannels = blockAlign;
            if (bytesPerSampleAllChannels != numChannels * (bitsPerSample / 8)) {
                throw new WavFormatException("Niespójny block align względem kanałów i bitów na próbkę.");
            }
            if (dataSize % bytesPerSampleAllChannels != 0) {
                throw new WavFormatException("Rozmiar danych audio nie jest wielokrotnością ramki próbki.");
            }

            long totalFrames = dataSize / bytesPerSampleAllChannels;
            double durationSeconds = totalFrames / (double) sampleRate;

            return new WavMetadata(
                sampleRate,
                numChannels,
                bitsPerSample,
                totalFrames,
                durationSeconds
            );
        }
    }
}
