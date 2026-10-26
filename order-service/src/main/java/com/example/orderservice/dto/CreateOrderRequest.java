package com.example.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/orders. The user is whoever the sign-in token names, never
 * a field here. item ends up in notifications, so control characters (line
 * breaks, escapes) are refused. An invalid body is a 400 (GlobalExceptionHandler).
 */
public record CreateOrderRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters") String item,
        @Positive Double amount) {
}
