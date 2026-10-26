package com.example.notificationservice.events;

/**
 * A record that can never be handled however often it is retried: not JSON,
 * an unknown event type, a required field missing. It goes straight to the
 * dead-letter topic.
 */
public class PermanentEventException extends RuntimeException {

    public PermanentEventException(String message) {
        super(message);
    }

    public PermanentEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
