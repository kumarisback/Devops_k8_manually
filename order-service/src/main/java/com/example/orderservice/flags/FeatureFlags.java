package com.example.orderservice.flags;

/**
 * Feature flags, decided at runtime by Unleash (gitops docs/FEATURE-FLAGS.md),
 * without a deploy. Every caller says what to do when the flag service can't
 * answer: the flag isn't defined, Unleash is unreachable, or the service runs
 * without it. Until the first answer arrives the fallback applies, and after
 * that the last answer, which the Unleash client keeps.
 */
public interface FeatureFlags {

    /** Kill switch: while off, order events wait in the outbox instead of going to Kafka. */
    String ORDER_EVENTS_PUBLISHING = "order-events-publishing";

    boolean isEnabled(String flag, boolean fallback);
}
