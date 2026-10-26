package com.example.orderservice.events;

import com.example.orderservice.model.Order;

/**
 * Tells the rest of the system about order changes. Kafka where the
 * environment has it (app.events.enabled=true), nothing otherwise; the
 * controller doesn't know which.
 */
public interface OrderEventPublisher {

    void orderCreated(Order order);
}
