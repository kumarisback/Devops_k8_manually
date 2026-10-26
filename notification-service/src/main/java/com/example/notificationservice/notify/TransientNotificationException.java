package com.example.notificationservice.notify;

/** A send failed for a reason that may pass (a mail server timing out): retried, then dead-lettered. */
public class TransientNotificationException extends RuntimeException {

    public TransientNotificationException(String message) {
        super(message);
    }
}
