package com.example.orderservice.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Fails the configured share of API requests with 500 (see
 * FaultInjectionProperties). It runs last among the filters, after the one
 * that records http_server_requests and after the request log, so the
 * failures show up in the metrics and logs exactly as a real bug's would.
 * Actuator (probes, metrics) is never failed: the pod stays ready and
 * scraped, as a bad release usually does.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class FaultInjectionFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(FaultInjectionFilter.class);

    private final FaultInjectionProperties properties;
    private final DoubleSupplier random;

    @Autowired
    public FaultInjectionFilter(FaultInjectionProperties properties) {
        this(properties, () -> ThreadLocalRandom.current().nextDouble());
    }

    FaultInjectionFilter(FaultInjectionProperties properties, DoubleSupplier random) {
        this.properties = properties;
        this.random = random;
        if (properties.enabled()) {
            logger.warn("Fault injection is on: {}% of API requests will fail with 500 (app.fault.error-rate)",
                    Math.round(properties.errorRate() * 100));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || request.getRequestURI().contains("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (random.getAsDouble() < properties.errorRate()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"injected fault (app.fault.error-rate)\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
