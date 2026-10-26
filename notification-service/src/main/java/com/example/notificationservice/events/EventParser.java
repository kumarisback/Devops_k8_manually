package com.example.notificationservice.events;

import com.example.notificationservice.NotificationProperties;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Reads the events order-service (topic orders) and user-service (topic
 * users) publish: Avro in the Confluent wire format, each record naming the
 * ID of the schema it was written with. That schema comes from the registry,
 * and the record is read as a GenericRecord in its own (writer's) schema, so
 * this service needs no generated classes and keeps working as producers add
 * fields. It reads only the fields it uses.
 *
 * A record that isn't a registered Avro record, an unknown event type or a
 * missing field is a {@link PermanentEventException} (dead-lettered at once);
 * a registry it can't reach is a {@link SchemaRegistryUnavailableException}
 * (retried). Every field is treated as untrusted input, whoever the producer
 * was.
 */
@Component
public class EventParser implements DisposableBean {

    private static final int MAX_FIELD_LENGTH = 80;

    private final KafkaAvroDeserializer avro = new KafkaAvroDeserializer();

    public EventParser(NotificationProperties properties) {
        avro.configure(Map.of("schema.registry.url", properties.schemaRegistryUrl(), "specific.avro.reader", false), false);
    }

    public IncomingEvent parse(String topic, byte[] value) {
        if (value == null) {
            throw new PermanentEventException("record has no value");
        }
        GenericRecord event;
        try {
            Object decoded = avro.deserialize(topic, value);
            if (!(decoded instanceof GenericRecord generic)) {
                throw new PermanentEventException("value is not an Avro record");
            }
            event = generic;
        } catch (SerializationException e) {
            if (causedBy(e, IOException.class)) {
                throw new SchemaRegistryUnavailableException(e);
            }
            throw new PermanentEventException("value is not an Avro record with a registered schema", e);
        }
        String type = event.getSchema().getName();
        String eventId = required(event, "eventId");
        String userId = required(event, "userId");
        String subject = switch (type) {
            case "OrderCreated" -> "Your order %s for %s is confirmed".formatted(text(event, "orderId"), text(event, "item"));
            case "UserRegistered" -> "Welcome, %s".formatted(text(event, "username"));
            default -> throw new PermanentEventException("unknown event type " + type);
        };
        return new IncomingEvent(eventId, type, userId, subject);
    }

    @Override
    public void destroy() {
        avro.close();
    }

    // A producer's free text goes into what users read: control characters
    // (line breaks that could forge a second line, terminal escapes) become
    // spaces, and each value is capped so a notification stays a notification.
    private static String text(GenericRecord event, String field) {
        String value = required(event, field).replaceAll("\\p{Cntrl}", " ").strip();
        return value.length() <= MAX_FIELD_LENGTH ? value : value.substring(0, MAX_FIELD_LENGTH) + "...";
    }

    private static String required(GenericRecord event, String field) {
        Schema.Field schemaField = event.getSchema().getField(field);
        Object value = schemaField == null ? null : event.get(field);
        if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
            throw new PermanentEventException("missing field " + field);
        }
        return text.toString();
    }

    private static boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
