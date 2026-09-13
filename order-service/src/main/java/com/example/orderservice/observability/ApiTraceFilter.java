package com.example.orderservice.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class ApiTraceFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(ApiTraceFilter.class);
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String TRACE_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || !traceId.matches("[A-Za-z0-9._-]{1,64}")) {
            traceId = UUID.randomUUID().toString();
        }

        long startedAt = System.nanoTime();
        response.setHeader(TRACE_HEADER, traceId);
        MDC.put(TRACE_MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            logger.info("api_request method={} path={} status={} duration_ms={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.remove(TRACE_MDC_KEY);
        }
    }
}