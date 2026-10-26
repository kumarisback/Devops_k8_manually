package com.example.orderservice.events;

import com.example.orderservice.model.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaOrderEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final KafkaOrderEventPublisher publisher = new KafkaOrderEventPublisher(template, mapper, "orders", registry,
            Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void publishesAnOrderCreatedEventKeyedByOrderId() throws Exception {
        CompletableFuture<SendResult<String, String>> sent = CompletableFuture.completedFuture(null);
        when(template.send(eq("orders"), eq("o-1"), org.mockito.ArgumentMatchers.anyString())).thenReturn(sent);

        publisher.orderCreated(new Order("o-1", "u-1", "Kafka book", 42.5, "CREATED"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(template).send(eq("orders"), eq("o-1"), json.capture());
        JsonNode event = mapper.readTree(json.getValue());
        assertThat(event.get("type").asText()).isEqualTo("OrderCreated");
        assertThat(event.get("eventId").asText()).hasSize(36);
        assertThat(event.get("occurredAt").asText()).isEqualTo("2026-10-26T10:00:00Z");
        assertThat(event.get("userId").asText()).isEqualTo("u-1");
        assertThat(registry.get("order.events").tag("outcome", "published").counter().count()).isEqualTo(1);
    }

    @Test
    void aFailedSendIsCountedNotThrown() {
        when(template.send(eq("orders"), eq("o-2"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unreachable")));

        publisher.orderCreated(new Order("o-2", "u-1", "book", 1.0, "CREATED"));

        assertThat(registry.get("order.events").tag("outcome", "failed").counter().count()).isEqualTo(1);
    }
}
