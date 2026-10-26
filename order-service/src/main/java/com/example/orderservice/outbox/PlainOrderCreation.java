package com.example.orderservice.outbox;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Where the environment has no Kafka (app.events.enabled=false): the order is saved, nothing else. */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "false", matchIfMissing = true)
public class PlainOrderCreation implements OrderCreation {

    private final OrderRepository orders;

    public PlainOrderCreation(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public Order create(String userId, String item, double amount) {
        return orders.save(new Order(null, userId, item, amount, "CREATED"));
    }
}
