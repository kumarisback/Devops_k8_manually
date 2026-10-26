package com.example.demo.events;

import com.example.demo.model.User;
import com.example.events.users.UserRegistered;
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
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Publishes UserRegistered events to Kafka as Avro (schema
 * src/main/avro/UserRegistered.avsc, checked by the schema registry), keyed
 * by user ID. It sends after the user is saved without waiting, so a Kafka
 * outage loses the event (logged and counted). order-service closes that
 * gap with a transactional outbox (roadmap step 13); doing the same here is
 * left as an exercise.
 */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class KafkaUserEventPublisher implements UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaUserEventPublisher.class);

    private final KafkaTemplate<String, Object> template;
    private final String topic;
    private final Clock clock;
    private final Counter published;
    private final Counter failed;

    public KafkaUserEventPublisher(KafkaTemplate<String, Object> template, @Value("${app.events.users-topic}") String topic,
                                   MeterRegistry registry) {
        this(template, topic, registry, Clock.systemUTC());
    }

    KafkaUserEventPublisher(KafkaTemplate<String, Object> template, String topic, MeterRegistry registry, Clock clock) {
        this.template = template;
        this.topic = topic;
        this.clock = clock;
        this.published = counter(registry, "published");
        this.failed = counter(registry, "failed");
    }

    @Override
    public void userRegistered(User user) {
        UserRegistered event = UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now(clock))
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .build();
        template.send(topic, user.getId(), event).whenComplete((result, ex) -> {
            if (ex == null) {
                published.increment();
            } else {
                failed.increment();
                log.error("user_event_publish_failed {} {} {}", kv("enduser.id", user.getId()), kv("event.id", event.getEventId()),
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
