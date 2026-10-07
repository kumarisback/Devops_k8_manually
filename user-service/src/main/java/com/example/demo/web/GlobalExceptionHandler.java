package com.example.demo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/**
 * Only a fault in this service is a 500. A bad request keeps its 4xx: Spring
 * MVC's own exceptions (malformed body, unknown path, wrong method,
 * ResponseStatusException, ...) through ResponseEntityExceptionHandler, a
 * failed sign-in as 401 and a denied method call as 403. The SLO alerts
 * count 5xx, so a client's mistake must not spend the error budget.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Not "logger": ResponseEntityExceptionHandler has a field of that name.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Wrong credentials at /auth/signin (AuthController audit-logs them). The
    // same message for every cause, so it doesn't reveal which users exist.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication failed");
    }

    // Sign-in couldn't be checked at all (e.g. MongoDB unreachable): Spring
    // Security wraps that in an AuthenticationException too, but it's our
    // fault, not the client's.
    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationServiceException(AuthenticationServiceException ex) {
        return handleUnhandledException(ex);
    }

    // A @PreAuthorize check failed for a signed-in user.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "forbidden", "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnhandledException(Exception ex) {
        log.error("unhandled_exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", "An unexpected error occurred");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }
}
