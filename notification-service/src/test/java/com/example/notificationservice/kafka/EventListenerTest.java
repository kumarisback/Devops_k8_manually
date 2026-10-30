package com.example.notificationservice.kafka;

import com.example.notificationservice.NotificationProperties;
import com.example.notificationservice.events.EventParser;
import com.example.notificationservice.events.IncomingEvent;
import com.example.notificationservice.notify.NotificationSender;
import com.example.notificationservice.notify.TransientNotificationException;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.example.notificationservice.events.AvroEvents.serialize;
import static com.example.notificationservice.events.AvroEvents.userRegistered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventListenerTest {

    private static final String SCOPE = "event-listener-test";

    private final List<IncomingEvent> sent = new ArrayList<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EventParser parser = new EventParser(new NotificationProperties(List.of("users"), "mock://" + SCOPE,
            new NotificationProperties.Retry(Duration.ofSeconds(1), 2, 4), 0, Duration.ZERO));

    @AfterEach
    void dropRegistry() {
        parser.destroy();
        MockSchemaRegistry.dropScope(SCOPE);
    }

    private ConsumerRecord<String, byte[]> record(String eventId, String userId, long offset) {
        return new ConsumerRecord<>("users", 0, offset, userId, serialize(SCOPE, "users", userRegistered(eventId, userId, "asha")));
    }

    @Test
    void aRedeliveredEventNotifiesOnce() {
        EventListener listener = new EventListener(parser, sent::add, new RecentEvents(), registry);
        ConsumerRecord<String, byte[]> record = record("e-1", "u-1", 7L);

        listener.onEvent(record);
        listener.onEvent(record);

        assertThat(sent).hasSize(1);
        assertThat(registry.get("notifications").tag("outcome", "duplicate").counter().count()).isEqualTo(1);
    }

    @Test
    void aFailedSendIsNotRememberedSoItsRetryIsSent() {
        ConsumerRecord<String, byte[]> record = record("e-2", "u-2", 8L);
        EventListener flaky = new EventListener(parser, new NotificationSender() {
            private boolean failedOnce;

            @Override
            public void send(IncomingEvent event) {
                if (!failedOnce) {
                    failedOnce = true;
                    throw new TransientNotificationException("provider timeout");
                }
                sent.add(event);
            }
        }, new RecentEvents(), registry);

        assertThatThrownBy(() -> flaky.onEvent(record)).isInstanceOf(TransientNotificationException.class);
        flaky.onEvent(record);

        assertThat(sent).hasSize(1);
    }
}
