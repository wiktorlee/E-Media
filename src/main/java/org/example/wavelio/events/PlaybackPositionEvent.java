package org.example.wavelio.events;

public record PlaybackPositionEvent(long currentMs, long durationMs) {}

