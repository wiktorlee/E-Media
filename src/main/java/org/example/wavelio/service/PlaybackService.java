package org.example.wavelio.service;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.nio.file.Path;

/**
 * Minimal WAV playback service for Phase 6A (play/stop + progress).
 */
public final class PlaybackService {

    private volatile MediaPlayer player;

    public synchronized void play(Path path, PlaybackListener listener) {
        stop();
        try {
            Media media = new Media(path.toUri().toString());
            MediaPlayer created = new MediaPlayer(media);
            player = created;
            created.setOnReady(() -> {
                long durationMs = toMillis(created.getTotalDuration());
                listener.onPositionChanged(0L, durationMs);
                listener.onStateChanged(PlaybackState.PLAYING);
                created.play();
            });
            created.currentTimeProperty().addListener((obs, oldV, newV) -> {
                long currentMs = toMillis(newV);
                long durationMs = toMillis(created.getTotalDuration());
                listener.onPositionChanged(currentMs, durationMs);
            });
            created.setOnEndOfMedia(() -> {
                long durationMs = toMillis(created.getTotalDuration());
                listener.onPositionChanged(durationMs, durationMs);
                listener.onStateChanged(PlaybackState.STOPPED);
                cleanup(created);
            });
            created.setOnError(() -> {
                listener.onStateChanged(PlaybackState.ERROR);
                listener.onError("Nie udało się odtworzyć pliku WAV.", created.getError());
                cleanup(created);
            });
        } catch (Exception e) {
            listener.onStateChanged(PlaybackState.ERROR);
            listener.onError("Nie udało się odtworzyć pliku WAV.", e);
        }
    }

    public synchronized void stop() {
        MediaPlayer current = player;
        if (current == null) {
            return;
        }
        try {
            current.stop();
        } finally {
            cleanup(current);
        }
    }

    private synchronized void cleanup(MediaPlayer p) {
        if (p == null) {
            return;
        }
        try {
            p.dispose();
        } finally {
            if (player == p) {
                player = null;
            }
        }
    }

    private static long toMillis(Duration d) {
        if (d == null || d.isUnknown() || d.isIndefinite()) {
            return 0L;
        }
        return Math.max(0L, Math.round(d.toMillis()));
    }

    public interface PlaybackListener {
        void onStateChanged(PlaybackState state);
        void onPositionChanged(long currentMs, long durationMs);
        void onError(String message, Throwable error);
    }
}

