package com.example.orderservice.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With app.fault.error-rate=1 every API request fails, and the failures are
 * in the request metrics the canary analysis reads (gitops
 * docs/ROLLOUTS.md); the pod's probes and scrapes are untouched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "app.fault.error-rate=1"})
class FaultInjectionTest {

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    private static HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void apiRequestsFailAndShowInTheMetrics() throws Exception {
        assertThat(get(apiPort, "/api/orders/status").statusCode()).isEqualTo(500);

        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).containsPattern("http_server_requests_seconds_count\\{[^}]*status=\"500\"");
    }
}
