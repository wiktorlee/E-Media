package org.example.wavelio.events;

import org.example.wavelio.model.SpectrogramResult;

public record SpectrogramReadyEvent(SpectrogramResult result) {}

