package com.example.orderservice.events;

import com.example.orderservice.model.Order;
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
 * Publishes order events to Kafka, keyed by order ID: every event about one
 * order lands in the same partition, so consumers see them in order, while
 * different orders spread over all partitions.
 *
 * Known gap: the order is saved first and the event sent afterwards, without
 * waiting for it. If Kafka is unreachable the order exists but nobody hears
 * about it; the failure is logged and counted, nothing more. The transactional
 * outbox (roadmap step 13) closes this gap: the event is written to the
 * database with the order and published from there.
 */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    private final KafkaTemplate<String, String> template;
    private final ObjectMapper mapper;
    private final String topic;
    private final Clock clock;
    private final Counter published;
    private final Counter failed;

    public KafkaOrderEventPublisher(KafkaTemplate<String, String> template, ObjectMapper mapper,
                                    @Value("${app.events.orders-topic}") String topic, MeterRegistry registry) {
        this(template, mapper, topic, registry, Clock.systemUTC());
    }

    KafkaOrderEventPublisher(KafkaTemplate<String, String> template, ObjectMapper mapper, String topic,
                             MeterRegistry registry, Clock clock) {
        this.template = template;
        this.mapper = mapper;
        this.topic = topic;
        this.clock = clock;
        this.published = counter(registry, "published");
        this.failed = counter(registry, "failed");
    }

    @Override
    public void orderCreated(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.of(order, Instant.now(clock));
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("OrderCreatedEvent is not serialisable", e);
        }
        template.send(topic, order.getId(), json).whenComplete((result, ex) -> {
            if (ex == null) {
                published.increment();
            } else {
                failed.increment();
                log.error("order_event_publish_failed {} {} {}", kv("order.id", order.getId()), kv("event.id", event.eventId()),
                        kv("messaging.destination.name", topic), ex);
            }
        });
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("order.events")
                .description("OrderCreated events by publish outcome")
                .tag("outcome", outcome)
                .register(registry);
    }
}
