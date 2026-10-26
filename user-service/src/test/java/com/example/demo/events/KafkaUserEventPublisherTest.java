package com.example.demo.events;

import com.example.demo.model.User;
import com.example.events.users.UserRegistered;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaUserEventPublisherTest {

    private static final String SCOPE = "user-registered-test";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
    private final KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(template, "users",
            new SimpleMeterRegistry(), Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void dropRegistry() {
        MockSchemaRegistry.dropScope(SCOPE);
    }

    @Test
    void publishesUserRegisteredWithoutCredentials() {
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(template.send(eq("users"), eq("u-1"), any())).thenReturn(sent);
        User user = new User("asha", "$2a$10$bcrypt-hash");
        user.setId("u-1");

        publisher.userRegistered(user);

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(template).send(eq("users"), eq("u-1"), value.capture());
        UserRegistered event = (UserRegistered) value.getValue();
        assertThat(event.getUsername()).isEqualTo("asha");
        assertThat(event.getSchema().getFields()).extracting(Schema.Field::name)
                .containsExactly("eventId", "occurredAt", "userId", "username");

        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer()) {
            serializer.configure(Map.of("schema.registry.url", "mock://" + SCOPE, "avro.remove.java.properties", true), false);
            String bytes = new String(serializer.serialize("users", event), StandardCharsets.ISO_8859_1);
            assertThat(bytes).doesNotContain("bcrypt").doesNotContain("password");
        }
    }

    @Test
    void newVersionsCanReadEveryPublishedOne() throws Exception {
        Schema published;
        try (InputStream in = getClass().getResourceAsStream("/avro/UserRegistered.v1.avsc")) {
            published = new Schema.Parser().parse(in);
        }
        assertThat(SchemaCompatibility.checkReaderWriterCompatibility(UserRegistered.getClassSchema(), published).getType())
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }
}
