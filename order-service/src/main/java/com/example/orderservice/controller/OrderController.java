package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getOrdersByUserId(@PathVariable String userId) {
        List<Order> orders = orderRepository.findByUserId(userId);

        // Seed mock data for easy verification
        if (orders.isEmpty()) {
            Order order1 = new Order(null, userId, "AWS EKS Architect Guide", 99.99, "COMPLETED");
            Order order2 = new Order(null, userId, "Kubernetes Microservices Pro Edition", 149.50, "SHIPPED");
            orderRepository.save(order1);
            orderRepository.save(order2);
            orders = orderRepository.findByUserId(userId);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("service", "backend-order (Order Microservice)");
        response.put("userId", userId);
        response.put("totalOrders", orders.size());
        response.put("orders", orders);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("service", "backend-order");
        status.put("status", "ONLINE");
        status.put("message", "Order Service running smoothly on EKS");
        return ResponseEntity.ok(status);
    }
}
