package com.example.orderservice.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/**
 * Only a fault in this service is a 500. Spring MVC's own exceptions
 * (malformed body, unknown path, wrong method, ResponseStatusException, ...)
 * keep their 4xx through ResponseEntityExceptionHandler. The SLO alerts count
 * 5xx, so a client's mistake must not spend the error budget.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Not "logger": ResponseEntityExceptionHandler has a field of that name.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnhandledException(Exception ex) {
        log.error("unhandled_exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_server_error", "message", "An unexpected error occurred"));
    }
}
