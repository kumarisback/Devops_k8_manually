package com.example.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.mock.web.MockHttpServletRequest;

class WebSecurityConfigTest {

    @Test
    void corsAllowsTracePropagationHeaders() {
        WebSecurityConfig config = new WebSecurityConfig();
        CorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/tasks");

        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedHeaders())
                .containsAll(List.of("traceparent", "tracestate", "baggage"));
    }
}
