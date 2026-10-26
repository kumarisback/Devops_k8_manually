package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.events.OrderEventPublisher;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

    @Autowired
    private OrderEventPublisher eventPublisher;

    // POST /api/orders. The order is saved, then an OrderCreated event goes to
    // Kafka (where the environment has it) for notification-service.
    @PostMapping({"", "/"})
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Span span = tracer.spanBuilder("orders.create").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("enduser.id", request.userId());
            Order order = orderRepository.save(new Order(null, request.userId(), request.item(), request.amount(), "CREATED"));
            span.setAttribute("order.id", order.getId());
            eventPublisher.orderCreated(order);
            auditLogger.info("order_created {} {}", kv("enduser.id", order.getUserId()), kv("order.id", order.getId()));
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            throw ex;
        } finally {
            span.end();
        }
    }

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
