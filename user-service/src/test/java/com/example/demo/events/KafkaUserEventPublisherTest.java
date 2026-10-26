package com.example.demo.events;

import com.example.demo.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaUserEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(template, mapper, "users",
            new SimpleMeterRegistry(), Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void publishesUserRegisteredWithoutCredentials() throws Exception {
        CompletableFuture<SendResult<String, String>> sent = CompletableFuture.completedFuture(null);
        when(template.send(eq("users"), eq("u-1"), anyString())).thenReturn(sent);
        User user = new User("asha", "$2a$10$bcrypt-hash");
        user.setId("u-1");

        publisher.userRegistered(user);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(template).send(eq("users"), eq("u-1"), json.capture());
        JsonNode event = mapper.readTree(json.getValue());
        assertThat(event.get("type").asText()).isEqualTo("UserRegistered");
        assertThat(event.get("username").asText()).isEqualTo("asha");
        assertThat(json.getValue()).doesNotContain("password").doesNotContain("bcrypt").doesNotContain("roles");
    }
}
