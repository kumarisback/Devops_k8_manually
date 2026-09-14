package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RestController
public class OrderController {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("order-service.business");
    private static final Logger auditLogger = LoggerFactory.getLogger("audit.order-service.orders");

    @Autowired
    private OrderRepository orderRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getOrdersByUserId(@PathVariable String userId) {
        Span span = tracer.spanBuilder("orders.lookup_by_user").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("enduser.id", userId);
            List<Order> orders = orderRepository.findByUserId(userId);

            if (orders.isEmpty()) {
                span.setAttribute("orders.seeded", true);
                Order order1 = new Order(null, userId, "AWS EKS Architect Guide", 99.99, "COMPLETED");
                Order order2 = new Order(null, userId, "Kubernetes Microservices Pro Edition", 149.50, "SHIPPED");
                orderRepository.save(order1);
                orderRepository.save(order2);
                orders = orderRepository.findByUserId(userId);
                auditLogger.info("orders_seeded {}", kv("enduser.id", userId));
            }

            span.setAttribute("orders.count", orders.size());
            auditLogger.info("orders_lookup_completed {} {}", kv("enduser.id", userId), kv("orders.count", orders.size()));

            Map<String, Object> response = new HashMap<>();
            response.put("service", "backend-order (Order Microservice)");
            response.put("userId", userId);
            response.put("totalOrders", orders.size());
            response.put("orders", orders);

            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            throw ex;
        } finally {
            span.end();
        }
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
