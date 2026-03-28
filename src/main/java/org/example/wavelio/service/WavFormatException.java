package org.example.wavelio.service;

public final class WavFormatException extends Exception {

    public WavFormatException(String message) {
        super(message);
    }

    public WavFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
