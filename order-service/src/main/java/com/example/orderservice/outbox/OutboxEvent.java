package com.example.orderservice.outbox;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * An event waiting to be published, stored in the same transaction as the
 * change it describes (the transactional outbox). OutboxRelay publishes it
 * to Kafka and marks it SENT; sent events expire after 7 days (TTL index on
 * sentAt, OutboxIndexes).
 */
@Document(collection = "outbox")
@Data
@NoArgsConstructor
public class OutboxEvent {

    public enum Status { PENDING, SENT }

    @Id
    private String id;

    /** Unique per event; consumers use it to recognise a redelivery. */
    private String eventId;

    /** The event type, e.g. OrderCreated. */
    private String type;

    /** What the event is about (the order ID): the Kafka record key. */
    private String aggregateId;

    /** The event's fields, turned into its Avro record when published. */
    private Map<String, Object> payload;

    private Instant occurredAt;
    private Status status;
    private int attempts;
    private Instant nextAttemptAt;
    /** Set while one relay instance is publishing it, so no other does. */
    private Instant lockedUntil;
    private String lastError;
    private Instant sentAt;

    public static OutboxEvent pending(String eventId, String type, String aggregateId, Map<String, Object> payload, Instant now) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setType(type);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        event.setOccurredAt(now);
        event.setStatus(Status.PENDING);
        event.setNextAttemptAt(now);
        return event;
    }
}
