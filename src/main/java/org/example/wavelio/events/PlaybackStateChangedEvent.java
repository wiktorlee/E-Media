package org.example.wavelio.events;

import org.example.wavelio.service.PlaybackState;

public record PlaybackStateChangedEvent(PlaybackState state) {}

