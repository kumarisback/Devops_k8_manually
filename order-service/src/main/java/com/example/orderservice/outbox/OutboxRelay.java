package com.example.orderservice.outbox;

import com.example.events.orders.OrderCreated;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Publishes the outbox to Kafka, oldest first, as Avro (the schema registry
 * checks every new schema version is backward compatible).
 *
 * Every replica runs it. Each event is claimed with an atomic findAndModify
 * that sets a short lease (lockedUntil), so no two replicas send the same
 * one; a replica that dies mid-send leaves a lease that simply runs out.
 * The send is synchronous: an event is marked SENT only after Kafka
 * acknowledged it (acks=all). A crash between the two sends it again, so
 * delivery is at least once and consumers deduplicate by eventId.
 *
 * On failure (Kafka or the registry down) the event goes back with an
 * exponential backoff (2s, 4s, ... up to 5 min) and the batch stops:
 * nothing is lost, the outbox just grows until it can be published,
 * which OutboxBacklogGrowing alerts on.
 */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final MongoTemplate mongo;
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;
    private final int batchSize;
    private final Clock clock;
    private final String instance;
    private final Counter published;
    private final Counter failed;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    public OutboxRelay(MongoTemplate mongo, KafkaTemplate<String, Object> kafka,
                       @Value("${app.events.orders-topic}") String topic,
                       @Value("${app.events.outbox.batch-size:100}") int batchSize,
                       MeterRegistry registry, Clock clock) {
        this.mongo = mongo;
        this.kafka = kafka;
        this.topic = topic;
        this.batchSize = batchSize;
        this.clock = clock;
        this.instance = hostname();
        this.published = Counter.builder("order.events").description("Order events by publish outcome")
                .tag("outcome", "published").register(registry);
        this.failed = Counter.builder("order.events").description("Order events by publish outcome")
                .tag("outcome", "failed").register(registry);
        Gauge.builder("outbox.pending", pending, AtomicLong::get)
                .description("Events in the outbox not yet published").register(registry);
        Gauge.builder("outbox.oldest.pending.age.seconds", oldestPendingAgeSeconds, AtomicLong::get)
                .description("Age of the oldest unpublished event").register(registry);
    }

    @Scheduled(fixedDelayString = "${app.events.outbox.poll-interval:500ms}")
    public void relay() {
        for (int i = 0; i < batchSize; i++) {
            OutboxEvent event = claimNext();
            if (event == null) {
                return;
            }
            if (!publish(event)) {
                return;
            }
        }
    }

    // How much is waiting, for the outbox_* gauges and their alerts.
    @Scheduled(fixedDelayString = "15s")
    public void measureBacklog() {
        Query due = new Query(Criteria.where("status").is(OutboxEvent.Status.PENDING));
        pending.set(mongo.count(due, OutboxEvent.class));
        OutboxEvent oldest = mongo.findOne(due.with(Sort.by("occurredAt")).limit(1), OutboxEvent.class);
        oldestPendingAgeSeconds.set(oldest == null ? 0 : Duration.between(oldest.getOccurredAt(), Instant.now(clock)).toSeconds());
    }

    OutboxEvent claimNext() {
        Instant now = Instant.now(clock);
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("status").is(OutboxEvent.Status.PENDING),
                Criteria.where("nextAttemptAt").lte(now),
                new Criteria().orOperator(Criteria.where("lockedUntil").is(null), Criteria.where("lockedUntil").lt(now))))
                .with(Sort.by("occurredAt"));
        Update lease = new Update().set("lockedUntil", now.plus(LEASE)).set("lockedBy", instance);
        return mongo.findAndModify(query, lease, FindAndModifyOptions.options().returnNew(true), OutboxEvent.class);
    }

    boolean publish(OutboxEvent event) {
        try {
            kafka.send(topic, event.getAggregateId(), toAvro(event)).get(30, TimeUnit.SECONDS);
            mongo.updateFirst(byId(event), new Update().set("status", OutboxEvent.Status.SENT).set("sentAt", Instant.now(clock))
                    .unset("lockedUntil").unset("lastError"), OutboxEvent.class);
            published.increment();
            return true;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            int attempts = event.getAttempts() + 1;
            // 2s, 4s, ... 256s, then the 5-minute cap (the shift is bounded
            // so it can't overflow).
            Duration backoff = Duration.ofSeconds(1L << Math.min(attempts, 9));
            backoff = backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
            mongo.updateFirst(byId(event), new Update().set("attempts", attempts).set("nextAttemptAt", Instant.now(clock).plus(backoff))
                    .set("lastError", String.valueOf(e.getMessage())).unset("lockedUntil"), OutboxEvent.class);
            failed.increment();
            log.error("order_event_publish_failed {} {} {} {}", kv("event.id", event.getEventId()), kv("order.id", event.getAggregateId()),
                    kv("messaging.destination.name", topic), kv("outbox.attempts", attempts), e);
            return false;
        }
    }

    static OrderCreated toAvro(OutboxEvent event) {
        if (!"OrderCreated".equals(event.getType())) {
            throw new IllegalStateException("unknown outbox event type " + event.getType());
        }
        Map<String, Object> p = event.getPayload();
        return OrderCreated.newBuilder()
                .setEventId(event.getEventId())
                .setOccurredAt(event.getOccurredAt())
                .setOrderId((String) p.get("orderId"))
                .setUserId((String) p.get("userId"))
                .setItem((String) p.get("item"))
                .setAmount(((Number) p.get("amount")).doubleValue())
                .setStatus((String) p.get("status"))
                .build();
    }

    private static Query byId(OutboxEvent event) {
        return new Query(Criteria.where("_id").is(event.getId()));
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
