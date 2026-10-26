package com.example.notificationservice.kafka;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Event IDs this replica handled recently, so a redelivered record (Kafka
 * delivers at least once: after a rebalance or a crash before the offset
 * commit) doesn't notify the user twice. It only remembers this replica's
 * last 10,000 events; a durable record of sent notifications is the
 * complete answer (a database table keyed by event ID).
 */
@Component
public class RecentEvents {

    private static final int CAPACITY = 10_000;

    private final Set<String> seen = Collections.newSetFromMap(Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > CAPACITY;
        }
    }));

    /** Whether this event was already handled here. */
    public boolean handled(String eventId) {
        return seen.contains(eventId);
    }

    /**
     * Records an event as handled. Call it only once the notification went
     * out: an event remembered before a failed send would be skipped as a
     * duplicate when it is retried, and silently lost.
     */
    public void remember(String eventId) {
        seen.add(eventId);
    }
}
