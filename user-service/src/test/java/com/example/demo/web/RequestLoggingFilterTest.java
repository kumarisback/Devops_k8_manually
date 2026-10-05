package com.example.demo.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("access.user-service.http");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        accessLogger.detachAppender(appender);
    }

    private static RequestLoggingFilter filter(HttpLoggingProperties.RequestBody mode, int maxBodyLength) {
        return new RequestLoggingFilter(new HttpLoggingProperties(mode, maxBodyLength), new ObjectMapper());
    }

    private static MockHttpServletRequest jsonPost(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/signin");
        request.setContextPath("/api");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    /** Stands in for Spring MVC: reads the body, records the route, sets the status. */
    private static FilterChain handler(int status) {
        return (req, res) -> {
            req.getInputStream().readAllBytes();
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/auth/signin");
            ((HttpServletResponse) res).setStatus(status);
        };
    }

    /** The kv(...) fields of the single logged event, as name -> value. */
    private Map<String, String> loggedFields() {
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMessage()).isEqualTo("http_request");
        Map<String, String> fields = new LinkedHashMap<>();
        Arrays.stream(event.getArgumentArray()).map(Object::toString).forEach(kv -> {
            int eq = kv.indexOf('=');
            fields.put(kv.substring(0, eq), kv.substring(eq + 1));
        });
        return fields;
    }

    @Test
    void logsStandardFieldsAndMaskedPayloadWhenAlways() throws Exception {
        filter(HttpLoggingProperties.RequestBody.ALWAYS, 2048).doFilter(
                jsonPost("{\"username\":\"alice\",\"password\":\"hunter2\",\"meta\":{\"accessToken\":\"abc\"}}"),
                new MockHttpServletResponse(), handler(201));

        Map<String, String> fields = loggedFields();
        assertThat(fields).containsEntry("http.request.method", "POST")
                .containsEntry("url.path", "/api/auth/signin")
                .containsEntry("http.route", "/api/auth/signin")
                .containsEntry("http.response.status_code", "201")
                .containsKey("duration_ms")
                .containsKey("http.request.body.size")
                .containsEntry("http.request.body",
                        "{\"username\":\"alice\",\"password\":\"<redacted>\",\"meta\":{\"accessToken\":\"<redacted>\"}}");
        assertThat(fields.keySet()).doesNotContain("http.response.body");
    }

    @Test
    void masksSecretKeysInAnySpellingAndDepth() throws Exception {
        filter(HttpLoggingProperties.RequestBody.ALWAYS, 2048).doFilter(
                jsonPost("{\"newPassword\":\"a\",\"client_secret\":\"b\",\"X-Api-Key\":\"c\","
                        + "\"items\":[{\"refreshToken\":\"d\",\"qty\":2}]}"),
                new MockHttpServletResponse(), handler(201));

        assertThat(loggedFields()).containsEntry("http.request.body",
                "{\"newPassword\":\"<redacted>\",\"client_secret\":\"<redacted>\",\"X-Api-Key\":\"<redacted>\","
                        + "\"items\":[{\"refreshToken\":\"<redacted>\",\"qty\":2}]}");
    }

    @Test
    void neverLogsPayloadByDefault() throws Exception {
        filter(HttpLoggingProperties.RequestBody.NEVER, 2048).doFilter(
                jsonPost("{\"item\":\"book\"}"), new MockHttpServletResponse(), handler(201));

        assertThat(loggedFields()).doesNotContainKey("http.request.body")
                .containsEntry("http.response.status_code", "201");
    }

    @Test
    void errorsModeLogsPayloadOnlyForFailures() throws Exception {
        RequestLoggingFilter filter = filter(HttpLoggingProperties.RequestBody.ERRORS, 2048);

        filter.doFilter(jsonPost("{\"item\":\"book\"}"), new MockHttpServletResponse(), handler(200));
        assertThat(loggedFields()).doesNotContainKey("http.request.body");

        appender.list.clear();
        filter.doFilter(jsonPost("{\"item\":\"book\"}"), new MockHttpServletResponse(), handler(500));
        assertThat(loggedFields()).containsEntry("http.request.body", "{\"item\":\"book\"}");
    }

    @Test
    void cutsLongPayloads() throws Exception {
        filter(HttpLoggingProperties.RequestBody.ALWAYS, 20).doFilter(
                jsonPost("{\"note\":\"" + "x".repeat(100) + "\"}"), new MockHttpServletResponse(), handler(201));

        String body = loggedFields().get("http.request.body");
        assertThat(body).hasSize(20 + RequestLoggingFilter.TRUNCATED.length())
                .endsWith(RequestLoggingFilter.TRUNCATED);
    }

    @Test
    void skipsPayloadsTooLargeToCapture() throws Exception {
        filter(HttpLoggingProperties.RequestBody.ALWAYS, 2048).doFilter(
                jsonPost("{\"note\":\"" + "x".repeat(RequestLoggingFilter.MAX_CAPTURE_BYTES) + "\"}"),
                new MockHttpServletResponse(), handler(201));

        assertThat(loggedFields()).doesNotContainKey("http.request.body")
                .containsEntry("http.request.body.size", String.valueOf(RequestLoggingFilter.MAX_CAPTURE_BYTES + 11));
    }

    @Test
    void recordsEscapedExceptionsAsServerErrors() {
        RequestLoggingFilter filter = filter(HttpLoggingProperties.RequestBody.ERRORS, 2048);
        FilterChain failing = (req, res) -> {
            req.getInputStream().readAllBytes();
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(jsonPost("{\"item\":\"book\"}"), new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(loggedFields()).containsEntry("http.response.status_code", "500")
                .containsEntry("error.type", "java.lang.IllegalStateException")
                .containsEntry("http.request.body", "{\"item\":\"book\"}");
    }

    @Test
    void skipsNonJsonPayloads() throws Exception {
        MockHttpServletRequest request = jsonPost("item=book&password=hunter2");
        request.setContentType("application/x-www-form-urlencoded");
        filter(HttpLoggingProperties.RequestBody.ALWAYS, 2048).doFilter(
                request, new MockHttpServletResponse(), handler(201));

        assertThat(loggedFields()).doesNotContainKey("http.request.body");
    }

    @Test
    void doesNotLogActuatorCalls() throws Exception {
        MockHttpServletRequest probe = new MockHttpServletRequest("GET", "/api/actuator/health/readiness");
        probe.setContextPath("/api");
        filter(HttpLoggingProperties.RequestBody.ALWAYS, 2048).doFilter(
                probe, new MockHttpServletResponse(), handler(200));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void omitsRouteWhenNoHandlerMatched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/nope");
        request.setContextPath("/api");
        filter(HttpLoggingProperties.RequestBody.NEVER, 2048).doFilter(
                request, new MockHttpServletResponse(), (req, res) -> ((HttpServletResponse) res).setStatus(404));

        assertThat(loggedFields()).doesNotContainKey("http.route")
                .containsEntry("http.response.status_code", "404");
    }

    @Test
    void propertiesDefaultToNeverAnd2048() {
        HttpLoggingProperties defaults = new HttpLoggingProperties(null, 0);
        assertThat(defaults.requestBody()).isEqualTo(HttpLoggingProperties.RequestBody.NEVER);
        assertThat(defaults.maxBodyLength()).isEqualTo(2048);
        assertThat(List.of(HttpLoggingProperties.RequestBody.values())).hasSize(3);
    }
}
