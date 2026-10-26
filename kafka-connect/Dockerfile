# Kafka Connect for the events archive (roadmap step 13): Strimzi's Kafka
# image plus Aiven's S3 sink connector. Strimzi runs it as the KafkaConnect
# events-connect (gitops streaming/components/events-archive).
#
# Keep the base image on the Strimzi and Kafka versions the cluster runs
# (gitops streaming/base/kafka.yaml). The plugin is pinned by checksum: a
# connector runs with the worker's Kafka identity and AWS role.

# ---- Unpack the plugin ----
FROM alpine:3.24.3 AS plugins

ADD --checksum=sha256:85661c4d3d49b85f4a65170a5c27464e4359760aa7140a24628e45323b6d7329 https://github.com/Aiven-Open/cloud-storage-connectors-for-apache-kafka/releases/download/v3.4.3/s3-sink-connector-for-apache-kafka-3.4.3.tar /tmp/s3-sink.tar
RUN mkdir -p /plugins/aiven-s3-sink && tar -xf /tmp/s3-sink.tar -C /plugins/aiven-s3-sink

# ---- Kafka Connect ----
FROM quay.io/strimzi/kafka:1.3.0-kafka-4.3.1

# Strimzi's plugin.path: each directory under it is one plugin, loaded in a
# class loader of its own. The base image's non-root user (1001) stays.
COPY --from=plugins /plugins/ /opt/kafka/plugins/
