package com.example.orderservice.outbox;

import com.example.events.orders.OrderCreated;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OrderCreated contract. The schema registry refuses an incompatible new
 * version at runtime; this test refuses it before the code is even built
 * into an image, against every version ever published (src/test/resources/avro).
 */
class OrderCreatedSchemaTest {

    private static final String SCOPE = "order-created-schema-test";

    @AfterEach
    void dropRegistry() {
        MockSchemaRegistry.dropScope(SCOPE);
    }

    @Test
    void newVersionsCanReadEveryPublishedOne() throws Exception {
        for (String version : new String[] {"OrderCreated.v1.avsc"}) {
            Schema published;
            try (InputStream in = getClass().getResourceAsStream("/avro/" + version)) {
                published = new Schema.Parser().parse(in);
            }
            SchemaCompatibility.SchemaPairCompatibility result =
                    SchemaCompatibility.checkReaderWriterCompatibility(OrderCreated.getClassSchema(), published);
            assertThat(result.getType()).as(version + ": " + result.getDescription())
                    .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
        }
    }

    @Test
    void roundTripsThroughTheRegistryAsTheRelayWritesIt() {
        Map<String, Object> config = Map.of("schema.registry.url", "mock://" + SCOPE, "auto.register.schemas", true,
                "avro.remove.java.properties", true);
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(); KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer()) {
            serializer.configure(config, false);
            deserializer.configure(config, false);
            OrderCreated event = OrderCreated.newBuilder().setEventId("e-1").setOccurredAt(Instant.parse("2026-10-26T10:00:00Z"))
                    .setOrderId("o-1").setUserId("asha").setItem("Kafka book").setAmount(42.5).setStatus("CREATED").build();

            byte[] bytes = serializer.serialize("orders", event);
            GenericRecord read = (GenericRecord) deserializer.deserialize("orders", bytes);

            assertThat(bytes[0]).as("Confluent wire format: magic byte, then the schema ID").isZero();
            assertThat(read.get("orderId").toString()).isEqualTo("o-1");
            assertThat(read.getSchema().getFullName()).isEqualTo("com.example.events.orders.OrderCreated");
        }
    }
}
