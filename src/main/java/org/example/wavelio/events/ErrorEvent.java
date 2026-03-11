package org.example.wavelio.events;

public record ErrorEvent(String message, Throwable cause) {
    public ErrorEvent(String message) {
        this(message, null);
    }
}
