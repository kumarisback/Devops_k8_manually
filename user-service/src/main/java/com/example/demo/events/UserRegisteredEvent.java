package com.example.demo.events;

import com.example.demo.model.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to topic users when someone signs up, as JSON. It carries no
 * password, hash or roles: consumers get only what they need to greet the user.
 */
public record UserRegisteredEvent(String eventId, String type, Instant occurredAt, String userId, String username) {

    public static UserRegisteredEvent of(User user, Instant now) {
        return new UserRegisteredEvent(UUID.randomUUID().toString(), "UserRegistered", now, user.getId(), user.getUsername());
    }
}
