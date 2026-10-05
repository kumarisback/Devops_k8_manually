package com.example.demo.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Writes one "API log" line per HTTP request (logger access.user-service.http,
 * message http_request). Field names follow the OpenTelemetry HTTP semantic
 * conventions, so this log, the ingress access log and the traces use the
 * same vocabulary:
 * <ul>
 *   <li>http.request.method, url.path (no query string: it can carry
 *       secrets), http.route (the route template, e.g.
 *       /api/tasks/{id}), http.response.status_code, duration_ms,
 *       http.request.body.size when the client sent a Content-Length, and
 *       error.type when an exception escaped the application.</li>
 *   <li>http.request.body: the request payload, only when
 *       app.http-logging.request-body allows it (see HttpLoggingProperties),
 *       only for JSON bodies up to 64 KiB that the application actually read,
 *       with secret fields masked and cut to max-body-length. Response bodies
 *       are never logged.</li>
 * </ul>
 * Actuator endpoints (Kubernetes probes, Prometheus scrapes) are not logged:
 * they are infrastructure traffic, not API calls.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger("access.user-service.http");

    /**
     * A JSON key containing any of these (case-insensitive, so accessToken,
     * new_password and X-Api-Key all count) has its value replaced with
     * "<redacted>", at any depth.
     */
    static final Pattern SECRET_KEYS = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|authorization|cookie|api[_-]?key|credit[_-]?card|card[_-]?number|cvv)");
    static final String REDACTED = "<redacted>";
    static final String TRUNCATED = "...(truncated)";
    /** Most of a body kept in memory for logging; larger bodies aren't logged. */
    static final int MAX_CAPTURE_BYTES = 64 * 1024;

    private final HttpLoggingProperties properties;
    private final ObjectMapper objectMapper;

    public RequestLoggingFilter(HttpLoggingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean capture = properties.requestBody() != HttpLoggingProperties.RequestBody.NEVER && isJson(request);
        // Keeps a copy of the body as the application reads it; the filter
        // never reads the body itself, so the request behaves exactly as
        // without logging.
        HttpServletRequest req = capture ? new ContentCachingRequestWrapper(request, MAX_CAPTURE_BYTES) : request;
        long start = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(req, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            // An exception that escapes here becomes a 500 later, in the
            // container, so the response doesn't say so yet.
            int status = failure != null && response.getStatus() < 400 ? 500 : response.getStatus();
            List<Object> fields = new ArrayList<>();
            fields.add(kv("http.request.method", request.getMethod()));
            fields.add(kv("url.path", request.getRequestURI()));
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (route != null) {
                fields.add(kv("http.route", request.getContextPath() + route));
            }
            fields.add(kv("http.response.status_code", status));
            fields.add(kv("duration_ms", durationMs));
            if (request.getContentLengthLong() >= 0) {
                fields.add(kv("http.request.body.size", request.getContentLengthLong()));
            }
            if (failure != null) {
                fields.add(kv("error.type", failure.getClass().getName()));
            }
            if (capture && shouldLogBody(status)) {
                String body = maskedBody(((ContentCachingRequestWrapper) req).getContentAsByteArray());
                if (body != null) {
                    fields.add(kv("http.request.body", body));
                }
            }
            logger.info("http_request", fields.toArray());
        }
    }

    private boolean shouldLogBody(int status) {
        return switch (properties.requestBody()) {
            case ALWAYS -> true;
            case ERRORS -> status >= 400;
            case NEVER -> false;
        };
    }

    private static boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(type) || type.getSubtype().endsWith("+json");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * The payload as compact JSON with secret fields masked and cut to
     * max-body-length, or null if empty or not valid JSON (including a body
     * over MAX_CAPTURE_BYTES, whose copy is incomplete): raw text is never
     * logged, because it can't be masked.
     */
    String maskedBody(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        String json;
        try {
            JsonNode tree = objectMapper.readTree(new String(content, StandardCharsets.UTF_8));
            json = objectMapper.writeValueAsString(mask(tree));
        } catch (IOException e) {
            return null;
        }
        if (json.length() > properties.maxBodyLength()) {
            json = json.substring(0, properties.maxBodyLength()) + TRUNCATED;
        }
        return json;
    }

    private static JsonNode mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SECRET_KEYS.matcher(field.getKey()).find()) {
                    field.setValue(object.textNode(REDACTED));
                } else {
                    mask(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(RequestLoggingFilter::mask);
        }
        return node;
    }
}
