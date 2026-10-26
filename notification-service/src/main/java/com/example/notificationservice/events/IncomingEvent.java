package com.example.notificationservice.events;

/**
 * An event this service reacts to, with what the notification needs.
 *
 * @param eventId unique per event; the same event delivered twice keeps it
 * @param type    OrderCreated or UserRegistered
 * @param userId  who to notify
 * @param subject the notification's subject line
 */
public record IncomingEvent(String eventId, String type, String userId, String subject) {
}
