package com.example.demo.events;

import com.example.demo.model.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Where the environment has no Kafka (app.events.enabled=false): events are not published. */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpUserEventPublisher implements UserEventPublisher {

    @Override
    public void userRegistered(User user) {
        // Nothing consumes user events in this environment.
    }
}
