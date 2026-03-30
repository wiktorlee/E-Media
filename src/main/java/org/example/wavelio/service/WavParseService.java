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
            ParsedHeader header = readAndValidateHeader(channel);
            long totalFrames = header.dataSize / header.blockAlign;
            double durationSeconds = totalFrames / (double) header.sampleRate;

            return new WavMetadata(
                header.sampleRate,
                header.numChannels,
                header.bitsPerSample,
                totalFrames,
                durationSeconds
            );
        }
    }

    public short[][] readPcm16Channels(Path path) throws IOException, WavFormatException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ParsedHeader header = readAndValidateHeader(channel);
            if (header.bitsPerSample != 16) {
                throw new WavFormatException("FFT aktualnie obsługuje tylko PCM 16-bit.");
            }
            int channels = header.numChannels;
            int frames = Math.toIntExact(header.dataSize / header.blockAlign);
            short[][] out = new short[channels][frames];

            ByteBuffer frame = ByteBuffer.allocate(header.blockAlign).order(ByteOrder.LITTLE_ENDIAN);
            long offset = header.dataStart;
            for (int i = 0; i < frames; i++) {
                frame.clear();
                if (!readFully(channel, frame, offset)) {
                    throw new WavFormatException("Nie udało się odczytać danych PCM.");
                }
                frame.flip();
                for (int ch = 0; ch < channels; ch++) {
                    out[ch][i] = frame.getShort();
                }
                offset += header.blockAlign;
            }
            return out;
        }
    }

    private ParsedHeader readAndValidateHeader(FileChannel channel) throws IOException, WavFormatException {
        long fileSize = channel.size();
        if (fileSize < 44) {
            throw new WavFormatException("Plik jest zbyt mały, aby był poprawnym WAV.");
        }

        ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        if (!readFully(channel, head, 0)) {
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
        Long dataStart = null;

        ByteBuffer chunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        while (position + 8 <= fileSize) {
            chunkHeader.clear();
            if (!readFully(channel, chunkHeader, position)) {
                break;
            }
            chunkHeader.flip();
            int chunkId = chunkHeader.getInt();
            long chunkSize = Integer.toUnsignedLong(chunkHeader.getInt());
            long chunkDataStart = position + 8;
            long afterChunk = chunkDataStart + chunkSize;
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
                if (!readFully(channel, fmt, chunkDataStart)) {
                    throw new WavFormatException("Nie udało się odczytać pełnego chunku fmt.");
                }
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
                dataSize = chunkSize;
                dataStart = chunkDataStart;
            }
            position = nextPosition;
        }

        if (numChannels == null || sampleRate == null || bitsPerSample == null || blockAlign == null) {
            throw new WavFormatException("Brak wymaganego chunku fmt .");
        }
        if (dataSize == null || dataStart == null) {
            throw new WavFormatException("Brak chunku data.");
        }
        int bytesPerSampleAllChannels = blockAlign;
        if (bytesPerSampleAllChannels != numChannels * (bitsPerSample / 8)) {
            throw new WavFormatException("Niespójny block align względem kanałów i bitów na próbkę.");
        }
        if (dataSize % bytesPerSampleAllChannels != 0) {
            throw new WavFormatException("Rozmiar danych audio nie jest wielokrotnością ramki próbki.");
        }
        return new ParsedHeader(numChannels, sampleRate, bitsPerSample, blockAlign, dataSize, dataStart);
    }

    private boolean readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, position);
            if (n < 0) {
                return false;
            }
            if (n == 0) {
                return false;
            }
            position += n;
        }
        return true;
    }

    private record ParsedHeader(
        int numChannels,
        int sampleRate,
        int bitsPerSample,
        int blockAlign,
        long dataSize,
        long dataStart
    ) {}
}
