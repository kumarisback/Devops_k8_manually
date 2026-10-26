package com.example.notificationservice.kafka;

import com.example.notificationservice.events.IncomingEvent;
import com.example.notificationservice.events.PermanentEventException;
import com.example.notificationservice.notify.NotificationSender;
import com.example.notificationservice.notify.TransientNotificationException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The listener and its error handling against a real (in-process) Kafka
 * broker: what reaches the sender, and what ends up in which dead-letter topic.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.notifications.retry.initial-interval=10ms",
})
@EmbeddedKafka(partitions = 1, topics = {"orders", "orders-dlq", "users", "users-dlq"})
@DirtiesContext
class DeadLetterIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @MockitoBean
    private NotificationSender sender;

    private Consumer<String, String> deadLetters;

    @BeforeEach
    void subscribeToDeadLetters() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlq-reader-" + System.nanoTime(), "false", broker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        deadLetters = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        broker.consumeFromEmbeddedTopics(deadLetters, "orders-dlq", "users-dlq");
    }

    @AfterEach
    void closeConsumer() {
        deadLetters.close();
    }

    @Test
    void aValidEventIsSentOnce() {
        template.send("orders", "o-1", """
                {"eventId":"e-ok","type":"OrderCreated","orderId":"o-1","userId":"u-1","item":"book"}""");

        verify(sender, timeout(20_000)).send(new IncomingEvent("e-ok", "OrderCreated", "u-1",
                "Your order o-1 for book is confirmed"));
    }

    @Test
    void aMalformedEventGoesStraightToItsDeadLetterTopic() {
        template.send("orders", "o-2", "not json");

        ConsumerRecord<String, String> parked = KafkaTestUtils.getSingleRecord(deadLetters, "orders-dlq", Duration.ofSeconds(20));
        assertThat(parked.value()).isEqualTo("not json");
        assertThat(header(parked, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).isEqualTo(PermanentEventException.class.getName());
        assertThat(header(parked, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo("orders");
    }

    @Test
    void aSendThatKeepsFailingIsRetriedThenDeadLettered() {
        doThrow(new TransientNotificationException("provider down")).when(sender).send(any());

        template.send("users", "u-9", """
                {"eventId":"e-fail","type":"UserRegistered","userId":"u-9","username":"ravi"}""");

        ConsumerRecord<String, String> parked = KafkaTestUtils.getSingleRecord(deadLetters, "users-dlq", Duration.ofSeconds(20));
        assertThat(header(parked, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).isEqualTo(TransientNotificationException.class.getName());
        // app.notifications.retry.max-attempts=4: the first try and three retries.
        verify(sender, times(4)).send(any());
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
