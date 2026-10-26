package com.example.notificationservice.kafka;

import com.example.notificationservice.events.EventParser;
import com.example.notificationservice.events.IncomingEvent;
import com.example.notificationservice.notify.NotificationSender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Turns order and user events into notifications. An exception thrown here
 * goes to the error handler (KafkaErrorHandling): retried if it may pass,
 * otherwise, or once retries run out, parked in the topic's dead-letter twin.
 */
@Component
public class EventListener {

    private static final Logger log = LoggerFactory.getLogger(EventListener.class);

    private final EventParser parser;
    private final NotificationSender sender;
    private final RecentEvents recentEvents;
    private final MeterRegistry registry;

    public EventListener(EventParser parser, NotificationSender sender, RecentEvents recentEvents, MeterRegistry registry) {
        this.parser = parser;
        this.sender = sender;
        this.recentEvents = recentEvents;
        this.registry = registry;
    }

    @KafkaListener(topics = "#{'${app.notifications.topics}'.split(',')}")
    public void onEvent(ConsumerRecord<String, String> record) {
        IncomingEvent event = parser.parse(record.value());
        if (recentEvents.handled(event.eventId())) {
            log.info("event_duplicate_skipped {} {}", kv("event.id", event.eventId()), kv("messaging.destination.name", record.topic()));
            sent(event.type(), "duplicate").increment();
            return;
        }
        sender.send(event);
        recentEvents.remember(event.eventId());
        sent(event.type(), "sent").increment();
    }

    private Counter sent(String eventType, String outcome) {
        return Counter.builder("notifications")
                .description("Events handled, by type and outcome")
                .tag("event_type", eventType)
                .tag("outcome", outcome)
                .register(registry);
    }
}
