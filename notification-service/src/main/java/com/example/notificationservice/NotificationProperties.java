package com.example.notificationservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * app.notifications.* in application.properties.
 *
 * @param topics               the topics to consume; each has a "-dlq" twin
 * @param schemaRegistryUrl    where the events' Avro schemas are looked up
 * @param retry                how often to retry a failure that may pass
 * @param simulatedFailureRate share of sends that fail on purpose (exercises only)
 */
@ConfigurationProperties("app.notifications")
public record NotificationProperties(List<String> topics, String schemaRegistryUrl, Retry retry, double simulatedFailureRate) {

    public record Retry(Duration initialInterval, double multiplier, int maxAttempts) {
    }
}
