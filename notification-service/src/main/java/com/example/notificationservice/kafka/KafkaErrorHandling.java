package com.example.notificationservice.kafka;

import com.example.notificationservice.NotificationProperties;
import com.example.notificationservice.events.PermanentEventException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * What happens when the listener throws. A failure that may pass is retried
 * in place with exponential backoff (1s, 2s, 4s by default); a permanent one,
 * or one still failing after the last attempt, is published to
 * {@code <topic>-dlq} with the exception in its headers, and the offset moves
 * on so the partition isn't blocked behind it. The record is parked byte for
 * byte as it arrived (values are read as raw bytes), so it can be replayed
 * exactly. The dead-letter topics are watched by an alert.
 *
 * Retrying in place holds up the rest of the partition for those seconds.
 * Spring's non-blocking retry topics avoid that at the cost of reordering;
 * for notifications, a few seconds' delay is the better trade.
 */
@Configuration
public class KafkaErrorHandling {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandling.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, byte[]> template, NotificationProperties properties,
                                          MeterRegistry registry) {
        // Partition -1: Kafka picks one, so a dead-letter topic doesn't need
        // as many partitions as its source.
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + "-dlq", -1));

        NotificationProperties.Retry retry = properties.retry();
        ExponentialBackOff backOff = new ExponentialBackOff(retry.initialInterval().toMillis(), retry.multiplier());
        backOff.setMaxAttempts(retry.maxAttempts() - 1);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            log.warn("event_dead_lettered {} {} {}", kv("messaging.destination.name", record.topic()),
                    kv("messaging.kafka.offset", record.offset()), kv("error.type", rootCause(ex).getClass().getName()));
            Counter.builder("notifications.dead.lettered")
                    .description("Records parked in a dead-letter topic")
                    .tag("topic", record.topic())
                    .register(registry)
                    .increment();
            deadLetters.accept(record, ex);
        }, backOff);
        handler.addNotRetryableExceptions(PermanentEventException.class, DeserializationException.class);
        return handler;
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
