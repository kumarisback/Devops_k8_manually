package com.example.notificationservice.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Reads the JSON events order-service (topic orders) and user-service (topic
 * users) publish. Values are plain JSON strings, so a producer in any language
 * can write them; anything malformed is a {@link PermanentEventException}. Every
 * field is treated as untrusted input, whoever the producer was.
 */
@Component
public class EventParser {

    private static final int MAX_FIELD_LENGTH = 80;

    private final ObjectMapper mapper;

    public EventParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public IncomingEvent parse(String json) {
        if (json == null) {
            throw new PermanentEventException("record has no value");
        }
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new PermanentEventException("value is not JSON", e);
        }
        String type = required(node, "type");
        String eventId = required(node, "eventId");
        String userId = required(node, "userId");
        String subject = switch (type) {
            case "OrderCreated" -> "Your order %s for %s is confirmed".formatted(text(node, "orderId"), text(node, "item"));
            case "UserRegistered" -> "Welcome, %s".formatted(text(node, "username"));
            default -> throw new PermanentEventException("unknown event type " + type);
        };
        return new IncomingEvent(eventId, type, userId, subject);
    }

    // A producer's free text goes into what users read: control characters
    // (line breaks that could forge a second line, terminal escapes) become
    // spaces, and each value is capped so a notification stays a notification.
    private static String text(JsonNode node, String field) {
        String value = required(node, field).replaceAll("\\p{Cntrl}", " ").strip();
        return value.length() <= MAX_FIELD_LENGTH ? value : value.substring(0, MAX_FIELD_LENGTH) + "...";
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new PermanentEventException("missing field " + field);
        }
        return value.asText();
    }
}
