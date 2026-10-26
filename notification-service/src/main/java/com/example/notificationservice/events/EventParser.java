package com.example.notificationservice.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Reads the JSON events order-service (topic orders) and user-service (topic
 * users) publish. Values are plain JSON strings, so a producer in any language
 * can write them; anything malformed is a {@link PermanentEventException}.
 */
@Component
public class EventParser {

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
            case "OrderCreated" -> "Your order %s for %s is confirmed".formatted(required(node, "orderId"), required(node, "item"));
            case "UserRegistered" -> "Welcome, %s".formatted(required(node, "username"));
            default -> throw new PermanentEventException("unknown event type " + type);
        };
        return new IncomingEvent(eventId, type, userId, subject);
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new PermanentEventException("missing field " + field);
        }
        return value.asText();
    }
}
