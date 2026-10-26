package com.example.orderservice.outbox;

import com.example.events.orders.OrderCreated;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OutboxRelay relay = new OutboxRelay(mongo, kafka, "orders", 10, registry, Clock.fixed(NOW, ZoneOffset.UTC));

    private static OutboxEvent pending(String id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "o-" + id);
        payload.put("userId", "asha");
        payload.put("item", "Kafka book");
        payload.put("amount", 42.5);
        payload.put("status", "CREATED");
        OutboxEvent event = OutboxEvent.pending("e-" + id, "OrderCreated", "o-" + id, payload, NOW);
        event.setId(id);
        return event;
    }

    @Test
    void publishesClaimedEventsAsAvroKeyedByOrderAndMarksThemSent() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OutboxEvent.class)))
                .thenReturn(pending("1"), (OutboxEvent) null);
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafka.send(eq("orders"), eq("o-1"), any())).thenReturn(sent);

        relay.relay();

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("orders"), eq("o-1"), value.capture());
        OrderCreated record = (OrderCreated) value.getValue();
        assertThat(record.getEventId()).isEqualTo("e-1");
        assertThat(record.getOccurredAt()).isEqualTo(NOW);
        assertThat(record.getAmount()).isEqualTo(42.5);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(OutboxEvent.class));
        assertThat(update.getValue().toString()).contains("SENT");
        assertThat(registry.get("order.events").tag("outcome", "published").counter().count()).isEqualTo(1);
    }

    @Test
    void aFailedSendIsRetriedLaterAndStopsTheBatch() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OutboxEvent.class)))
                .thenReturn(pending("1"), pending("2"));
        when(kafka.send(eq("orders"), any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unreachable")));

        relay.relay();

        // Only the first event was tried: the second waits for the next run.
        verify(kafka, times(1)).send(eq("orders"), any(), any());
        verify(mongo, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OutboxEvent.class));
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(OutboxEvent.class));
        assertThat(update.getValue().toString()).contains("attempts").contains("nextAttemptAt").doesNotContain("SENT");
        assertThat(registry.get("order.events").tag("outcome", "failed").counter().count()).isEqualTo(1);
    }

    @Test
    void nothingDueNothingSent() {
        relay.relay();

        verify(kafka, never()).send(any(), any(), any());
    }
}
