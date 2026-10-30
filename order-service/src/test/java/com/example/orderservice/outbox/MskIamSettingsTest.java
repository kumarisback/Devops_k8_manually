package com.example.orderservice.outbox;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amazon MSK (roadmap step 14): the environment variables the gitops chart
 * sets with kafka.msk.bootstrapServers must turn into IAM sign-in, with no
 * code change. Building a producer loads the login module and callback
 * handler and parses the JAAS line, without connecting anywhere.
 */
class MskIamSettingsTest {

    private static final Map<String, Object> CHART_ENV = Map.of(
            "SPRING_KAFKA_BOOTSTRAP_SERVERS", "boot-abc123.c1.kafka-serverless.us-east-1.amazonaws.com:9098",
            "SPRING_KAFKA_SECURITY_PROTOCOL", "SASL_SSL",
            "SPRING_KAFKA_PROPERTIES_SASL_MECHANISM", "AWS_MSK_IAM",
            "SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG", "software.amazon.msk.auth.iam.IAMLoginModule required;",
            "SPRING_KAFKA_PROPERTIES_SASL_CLIENT_CALLBACK_HANDLER_CLASS", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");

    @Test
    void theChartsMskVariablesSignInWithIam() {
        Binder binder = new Binder(ConfigurationPropertySources.from(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, CHART_ENV)));
        Map<String, Object> producer = binder.bind("spring.kafka", KafkaProperties.class).get().buildProducerProperties(null);

        assertThat(producer)
                .containsEntry("bootstrap.servers", java.util.List.of("boot-abc123.c1.kafka-serverless.us-east-1.amazonaws.com:9098"))
                .containsEntry("security.protocol", "SASL_SSL")
                .containsEntry("sasl.mechanism", "AWS_MSK_IAM")
                .containsEntry("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;")
                .containsEntry("sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");

        // A producer resolves its bootstrap host when built; the MSK name above
        // doesn't exist.
        producer.put("bootstrap.servers", "localhost:9098");
        producer.put("key.serializer", StringSerializer.class);
        producer.put("value.serializer", StringSerializer.class);
        try (KafkaProducer<String, String> client = new KafkaProducer<>(producer)) {
            assertThat(client.metrics()).isNotEmpty();
        }
    }
}
