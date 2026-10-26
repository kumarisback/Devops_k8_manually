package com.example.orderservice.outbox;

import com.example.orderservice.model.Order;

/**
 * Creates an order, and where the environment runs Kafka, records the
 * OrderCreated event with it (TransactionalOutboxOrderCreation); elsewhere
 * just saves it (PlainOrderCreation).
 */
public interface OrderCreation {

    Order create(String userId, String item, double amount);
}
