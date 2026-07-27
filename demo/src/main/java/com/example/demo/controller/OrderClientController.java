package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user-orders")
public class OrderClientController {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${order.service.url:http://backend-order}")
    private String orderServiceUrl;

    @GetMapping("/health-check")
    public ResponseEntity<?> checkOrderServiceHealth() {
        try {
            String targetUrl = orderServiceUrl + "/api/orders/actuator/health";
            ResponseEntity<String> response = restTemplate.getForEntity(targetUrl, String.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("source", "user-service");
            result.put("targetService", "backend-order");
            result.put("targetUrl", targetUrl);
            result.put("status", response.getStatusCode().toString());
            result.put("body", response.getBody());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("source", "user-service");
            errorResult.put("targetService", "backend-order");
            errorResult.put("error", e.getMessage());
            return ResponseEntity.status(503).body(errorResult);
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserOrders(@PathVariable String userId) {
        try {
            String targetUrl = orderServiceUrl + "/api/orders/user/" + userId;
            ResponseEntity<Object> response = restTemplate.getForEntity(targetUrl, Object.class);

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("userServiceStatus", "UP");
            result.put("ordersFromOrderService", response.getBody());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("userId", userId);
            errorResult.put("error", "Failed to contact Order Service: " + e.getMessage());
            return ResponseEntity.status(502).body(errorResult);
        }
    }
}
