package com.example.orderservice.events;

import com.example.orderservice.model.Order;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to topic orders when an order is created, as JSON. eventId is
 * unique per event, so a consumer can recognise a redelivery; consumers
 * must ignore fields they don't know, so fields can be added later.
 */
public record OrderCreatedEvent(String eventId, String type, Instant occurredAt, String orderId, String userId,
                                String item, Double amount, String status) {

    public static OrderCreatedEvent of(Order order, Instant now) {
        return new OrderCreatedEvent(UUID.randomUUID().toString(), "OrderCreated", now, order.getId(), order.getUserId(),
                order.getItem(), order.getAmount(), order.getStatus());
    }
}
