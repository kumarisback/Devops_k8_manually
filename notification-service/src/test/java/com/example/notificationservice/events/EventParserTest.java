package com.example.notificationservice.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventParserTest {

    private final EventParser parser = new EventParser(new ObjectMapper());

    @Test
    void readsAnOrderCreatedEvent() {
        IncomingEvent event = parser.parse("""
                {"eventId":"e-1","type":"OrderCreated","occurredAt":"2026-10-26T10:00:00Z",
                 "orderId":"o-1","userId":"u-1","item":"Kafka book","amount":42.5,"status":"CREATED"}""");

        assertThat(event).isEqualTo(new IncomingEvent("e-1", "OrderCreated", "u-1", "Your order o-1 for Kafka book is confirmed"));
    }

    @Test
    void readsAUserRegisteredEvent() {
        IncomingEvent event = parser.parse("""
                {"eventId":"e-2","type":"UserRegistered","userId":"u-2","username":"asha"}""");

        assertThat(event.subject()).isEqualTo("Welcome, asha");
    }

    @Test
    void anythingMalformedIsPermanent() {
        assertThatThrownBy(() -> parser.parse("not json")).isInstanceOf(PermanentEventException.class);
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(PermanentEventException.class);
        assertThatThrownBy(() -> parser.parse("""
                {"eventId":"e-3","type":"OrderShipped","userId":"u-1"}"""))
                .isInstanceOf(PermanentEventException.class).hasMessageContaining("unknown event type");
        assertThatThrownBy(() -> parser.parse("""
                {"eventId":"e-4","type":"OrderCreated","orderId":"o-1","item":"x"}"""))
                .isInstanceOf(PermanentEventException.class).hasMessageContaining("userId");
    }
}
