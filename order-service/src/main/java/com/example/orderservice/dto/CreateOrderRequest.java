package com.example.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of POST /api/orders. An invalid one is a 400 (GlobalExceptionHandler). */
public record CreateOrderRequest(
        @NotBlank @Size(max = 64) String userId,
        @NotBlank @Size(max = 200) String item,
        @Positive Double amount) {
}
