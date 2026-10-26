package com.example.orderservice.events;

import com.example.orderservice.model.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Where the environment has no Kafka (app.events.enabled=false): events are not published. */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpOrderEventPublisher implements OrderEventPublisher {

    @Override
    public void orderCreated(Order order) {
        // Nothing to tell: no environment component consumes order events here.
    }
}
