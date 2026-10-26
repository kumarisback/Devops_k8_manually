package com.example.demo.events;

import com.example.demo.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Publishes user events to Kafka, keyed by user ID. Like order-service's
 * publisher it sends after the user is saved without waiting, so a Kafka
 * outage loses the event (logged and counted); see roadmap step 13 for the
 * outbox that fixes it.
 */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class KafkaUserEventPublisher implements UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaUserEventPublisher.class);

    private final KafkaTemplate<String, String> template;
    private final ObjectMapper mapper;
    private final String topic;
    private final Clock clock;
    private final Counter published;
    private final Counter failed;

    public KafkaUserEventPublisher(KafkaTemplate<String, String> template, ObjectMapper mapper,
                                   @Value("${app.events.users-topic}") String topic, MeterRegistry registry) {
        this(template, mapper, topic, registry, Clock.systemUTC());
    }

    KafkaUserEventPublisher(KafkaTemplate<String, String> template, ObjectMapper mapper, String topic,
                            MeterRegistry registry, Clock clock) {
        this.template = template;
        this.mapper = mapper;
        this.topic = topic;
        this.clock = clock;
        this.published = counter(registry, "published");
        this.failed = counter(registry, "failed");
    }

    @Override
    public void userRegistered(User user) {
        UserRegisteredEvent event = UserRegisteredEvent.of(user, Instant.now(clock));
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("UserRegisteredEvent is not serialisable", e);
        }
        template.send(topic, user.getId(), json).whenComplete((result, ex) -> {
            if (ex == null) {
                published.increment();
            } else {
                failed.increment();
                log.error("user_event_publish_failed {} {} {}", kv("enduser.id", user.getId()), kv("event.id", event.eventId()),
                        kv("messaging.destination.name", topic), ex);
            }
        });
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("user.events")
                .description("UserRegistered events by publish outcome")
                .tag("outcome", outcome)
                .register(registry);
    }
}
