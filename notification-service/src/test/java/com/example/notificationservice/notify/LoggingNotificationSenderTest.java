package com.example.notificationservice.notify;

import com.example.notificationservice.NotificationProperties;
import com.example.notificationservice.events.IncomingEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingNotificationSenderTest {

    private static final IncomingEvent EVENT = new IncomingEvent("e-1", "OrderCreated", "u-1", "Your order o-1 is confirmed");

    private static LoggingNotificationSender sender(double failureRate, Duration latency) {
        return new LoggingNotificationSender(new NotificationProperties(List.of("orders"), "mock://sender-test",
                new NotificationProperties.Retry(Duration.ofSeconds(1), 2, 4), failureRate, latency));
    }

    @Test
    void sendsAtOnceByDefault() {
        long start = System.nanoTime();
        sender(0, Duration.ZERO).send(EVENT);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(50));
    }

    // The step-15 exercises slow every send down to build a backlog.
    @Test
    void takesTheSimulatedLatency() {
        long start = System.nanoTime();
        sender(0, Duration.ofMillis(120)).send(EVENT);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(120));
    }

    @Test
    void anInterruptedSendFailsTransientlyAndKeepsTheInterrupt() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> sender(0, Duration.ofSeconds(5)).send(EVENT))
                    .isInstanceOf(TransientNotificationException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void failsEverySendAtAFailureRateOfOne() {
        assertThatThrownBy(() -> sender(1, Duration.ZERO).send(EVENT)).isInstanceOf(TransientNotificationException.class);
    }
}
