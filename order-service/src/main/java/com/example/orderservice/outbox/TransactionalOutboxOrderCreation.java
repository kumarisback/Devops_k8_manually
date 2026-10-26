package com.example.orderservice.outbox;

import com.example.orderservice.model.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The transactional outbox: the order and its OrderCreated event are written
 * in one MongoDB transaction, so either both exist or neither does. There is
 * no window in which the order is saved but its event lost (the gap step 12's
 * direct publish had), and creating an order never waits for Kafka or the
 * schema registry: OutboxRelay publishes the event afterwards.
 *
 * MongoDB transactions need a replica set (Atlas is one; the lab's MongoDB
 * runs as a one-member replica set).
 */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class TransactionalOutboxOrderCreation implements OrderCreation {

    private final MongoTemplate mongo;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public TransactionalOutboxOrderCreation(MongoTemplate mongo, TransactionTemplate transactions, Clock clock) {
        this.mongo = mongo;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Order create(String userId, String item, double amount) {
        return transactions.execute(status -> {
            Order order = mongo.insert(new Order(null, userId, item, amount, "CREATED"));
            Instant now = Instant.now(clock);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", order.getId());
            payload.put("userId", order.getUserId());
            payload.put("item", order.getItem());
            payload.put("amount", order.getAmount());
            payload.put("status", order.getStatus());
            mongo.insert(OutboxEvent.pending(UUID.randomUUID().toString(), "OrderCreated", order.getId(), payload, now));
            return order;
        });
    }
}
