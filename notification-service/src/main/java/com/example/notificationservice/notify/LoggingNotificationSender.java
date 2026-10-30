package com.example.notificationservice.notify;

import com.example.notificationservice.NotificationProperties;
import com.example.notificationservice.events.IncomingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Stands in for an e-mail or push provider: a notification is "sent" by
 * writing an audit line. Swapping in a real provider means implementing
 * {@link NotificationSender}; nothing else changes.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger auditLogger = LoggerFactory.getLogger("audit.notification-service.notifications");

    private final double simulatedFailureRate;
    private final Duration simulatedLatency;

    public LoggingNotificationSender(NotificationProperties properties) {
        this.simulatedFailureRate = properties.simulatedFailureRate();
        this.simulatedLatency = properties.simulatedLatency();
    }

    @Override
    public void send(IncomingEvent event) {
        if (!simulatedLatency.isZero()) {
            try {
                Thread.sleep(simulatedLatency);
            } catch (InterruptedException e) {
                // Shutting down: the record isn't committed and comes again.
                Thread.currentThread().interrupt();
                throw new TransientNotificationException("interrupted");
            }
        }
        if (simulatedFailureRate > 0 && ThreadLocalRandom.current().nextDouble() < simulatedFailureRate) {
            throw new TransientNotificationException("simulated provider timeout");
        }
        auditLogger.info("notification_sent {} {} {} {}", kv("enduser.id", event.userId()), kv("notification.channel", "email"),
                kv("event.type", event.type()), kv("event.id", event.eventId()));
    }
}
