package com.example.notificationservice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * app.notifications.* in application.properties.
 *
 * @param topics               the topics to consume; each has a "-dlq" twin
 * @param schemaRegistryUrl    where the events' Avro schemas are looked up
 * @param retry                how often to retry a failure that may pass
 * @param simulatedFailureRate share of sends that fail on purpose (exercises only)
 * @param simulatedLatency     time each send takes on purpose, like a slow provider (exercises only)
 */
@ConfigurationProperties("app.notifications")
public record NotificationProperties(List<String> topics, String schemaRegistryUrl, Retry retry, double simulatedFailureRate,
                                     @DefaultValue("0ms") Duration simulatedLatency) {

    public record Retry(Duration initialInterval, double multiplier, int maxAttempts) {
    }
}
