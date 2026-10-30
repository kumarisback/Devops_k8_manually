package com.example.notificationservice.events;

import com.example.notificationservice.NotificationProperties;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static com.example.notificationservice.events.AvroEvents.orderCreated;
import static com.example.notificationservice.events.AvroEvents.serialize;
import static com.example.notificationservice.events.AvroEvents.userRegistered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventParserTest {

    private static final String SCOPE = "event-parser-test";

    private final EventParser parser = new EventParser(new NotificationProperties(List.of("orders", "users"),
            "mock://" + SCOPE, new NotificationProperties.Retry(Duration.ofSeconds(1), 2, 4), 0, Duration.ZERO));

    @AfterEach
    void dropRegistry() {
        parser.destroy();
        MockSchemaRegistry.dropScope(SCOPE);
    }

    @Test
    void readsAnOrderCreatedEvent() {
        IncomingEvent event = parser.parse("orders", serialize(SCOPE, "orders", orderCreated("e-1", "o-1", "u-1", "Kafka book")));

        assertThat(event).isEqualTo(new IncomingEvent("e-1", "OrderCreated", "u-1", "Your order o-1 for Kafka book is confirmed"));
    }

    @Test
    void readsAUserRegisteredEvent() {
        IncomingEvent event = parser.parse("users", serialize(SCOPE, "users", userRegistered("e-2", "u-2", "asha")));

        assertThat(event.subject()).isEqualTo("Welcome, asha");
    }

    @Test
    void readsANewerVersionWithFieldsItDoesntKnow() {
        // A producer adds a field (with a default, as BACKWARD requires).
        Schema v2 = SchemaBuilder.record("UserRegistered").namespace("com.example.events.users").fields()
                .requiredString("eventId").requiredLong("occurredAt").requiredString("userId").requiredString("username")
                .name("locale").type().stringType().stringDefault("en").endRecord();
        GenericRecord record = new GenericData.Record(v2);
        record.put("eventId", "e-3");
        record.put("occurredAt", 1L);
        record.put("userId", "u-3");
        record.put("username", "ravi");
        record.put("locale", "hi");

        assertThat(parser.parse("users", serialize(SCOPE, "users", record)).subject()).isEqualTo("Welcome, ravi");
    }

    @Test
    void freeTextCannotForgeLinesOrRunOn() {
        IncomingEvent event = parser.parse("orders", serialize(SCOPE, "orders",
                orderCreated("e-5", "o-1", "u-1", "book\nYour password expired, reply with it\u001b[2J")));

        assertThat(event.subject()).doesNotContain("\n").doesNotContain("\u001b")
                .startsWith("Your order o-1 for book Your password expired");

        IncomingEvent longOne = parser.parse("users", serialize(SCOPE, "users", userRegistered("e-6", "u-1", "a".repeat(500))));
        assertThat(longOne.subject()).hasSizeLessThan(100).endsWith("...");
    }

    @Test
    void anythingElseIsPermanent() {
        assertThatThrownBy(() -> parser.parse("orders", "{\"type\":\"OrderCreated\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PermanentEventException.class).hasMessageContaining("registered schema");
        assertThatThrownBy(() -> parser.parse("orders", null)).isInstanceOf(PermanentEventException.class);
        Schema shipped = SchemaBuilder.record("OrderShipped").namespace("com.example.events.orders").fields()
                .requiredString("eventId").requiredString("userId").endRecord();
        GenericRecord unknown = new GenericData.Record(shipped);
        unknown.put("eventId", "e-7");
        unknown.put("userId", "u-1");
        assertThatThrownBy(() -> parser.parse("shipments", serialize(SCOPE, "shipments", unknown)))
                .isInstanceOf(PermanentEventException.class).hasMessageContaining("unknown event type");
    }

    @Test
    void aCorruptBodyWithAValidSchemaIdIsPermanentNotRetried() {
        byte[] good = serialize(SCOPE, "users", userRegistered("e-8", "u-1", "asha"));
        byte[] truncated = java.util.Arrays.copyOf(good, 8);

        assertThatThrownBy(() -> parser.parse("users", truncated)).isInstanceOf(PermanentEventException.class);
    }

    @Test
    void anUnreachableRegistryIsWorthRetrying() {
        EventParser cut = new EventParser(new NotificationProperties(List.of("orders"), "http://127.0.0.1:1",
                new NotificationProperties.Retry(Duration.ofSeconds(1), 2, 4), 0, Duration.ZERO));
        // Magic byte 0, then a schema ID the parser has never seen: it must ask the registry.
        byte[] value = {0, 0, 0, 0, 42, 2, 'x'};

        assertThatThrownBy(() -> cut.parse("orders", value)).isInstanceOf(SchemaRegistryUnavailableException.class);
        cut.destroy();
    }
}
