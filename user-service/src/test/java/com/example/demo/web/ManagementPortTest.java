package com.example.demo.web;

import com.example.demo.security.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator lives on the management port only. The API port is what the
 * gateway and the service mesh expose; health and metrics must not be
 * reachable through it (gitops docs/MESH.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class ManagementPortTest {

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.signing-key", TestKeys::rsaPkcs8Pem);
    }

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    private static int get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void actuatorIsOnlyOnTheManagementPort() throws Exception {
        assertThat(managementPort).isNotEqualTo(apiPort);
        assertThat(get(apiPort, "/api/actuator/prometheus")).isEqualTo(404);
        assertThat(get(apiPort, "/api/actuator/health")).isEqualTo(404);

        assertThat(get(managementPort, "/actuator/prometheus")).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/liveness")).isEqualTo(200);
    }
}
