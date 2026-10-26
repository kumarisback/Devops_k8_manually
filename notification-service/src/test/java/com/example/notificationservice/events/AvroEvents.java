package com.example.notificationservice.events;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Events as the producers write them: Avro in the Confluent wire format,
 * registered in an in-memory registry (mock://scope). The schemas in
 * src/test/resources/contracts are copies of the producers' (order-service,
 * user-service src/main/avro): this service's side of the contract.
 */
public final class AvroEvents {

    private AvroEvents() {
    }

    public static Schema contract(String name) {
        try (InputStream in = AvroEvents.class.getResourceAsStream("/contracts/" + name + ".avsc")) {
            return new Schema.Parser().parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] serialize(String scope, String topic, GenericRecord record) {
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer()) {
            serializer.configure(Map.of("schema.registry.url", "mock://" + scope, "auto.register.schemas", true), false);
            return serializer.serialize(topic, record);
        }
    }

    public static GenericRecord orderCreated(String eventId, String orderId, String userId, String item) {
        GenericRecord record = new GenericData.Record(contract("OrderCreated"));
        record.put("eventId", eventId);
        record.put("occurredAt", 1793008800000L);
        record.put("orderId", orderId);
        record.put("userId", userId);
        record.put("item", item);
        record.put("amount", 42.5);
        record.put("status", "CREATED");
        return record;
    }

    public static GenericRecord userRegistered(String eventId, String userId, String username) {
        GenericRecord record = new GenericData.Record(contract("UserRegistered"));
        record.put("eventId", eventId);
        record.put("occurredAt", 1793008800000L);
        record.put("userId", userId);
        record.put("username", username);
        return record;
    }
}
