package com.example.notificationservice.kafka;

import com.example.notificationservice.events.EventParser;
import com.example.notificationservice.events.IncomingEvent;
import com.example.notificationservice.notify.NotificationSender;
import com.example.notificationservice.notify.TransientNotificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventListenerTest {

    private final List<IncomingEvent> sent = new ArrayList<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EventListener listener = new EventListener(new EventParser(new ObjectMapper()), sent::add,
            new RecentEvents(), registry);

    @Test
    void aRedeliveredEventNotifiesOnce() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("users", 0, 7L, "u-1",
                """
                {"eventId":"e-1","type":"UserRegistered","userId":"u-1","username":"asha"}""");

        listener.onEvent(record);
        listener.onEvent(record);

        assertThat(sent).hasSize(1);
        assertThat(registry.get("notifications").tag("outcome", "duplicate").counter().count()).isEqualTo(1);
    }

    @Test
    void aFailedSendIsNotRememberedSoItsRetryIsSent() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("users", 0, 8L, "u-2",
                """
                {"eventId":"e-2","type":"UserRegistered","userId":"u-2","username":"ravi"}""");
        EventListener flaky = new EventListener(new EventParser(new ObjectMapper()), new NotificationSender() {
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
