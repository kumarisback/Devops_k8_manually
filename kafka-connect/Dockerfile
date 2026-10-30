# Our Kafka image (roadmap steps 13 and 14): Strimzi's Kafka image plus
# Aiven's S3 sink connector, which Strimzi runs as the KafkaConnect
# events-connect (gitops streaming/components/events-archive), and Amazon
# MSK's IAM sign-in, for Kafka's command-line tools against MSK (gitops
# msk/topics-job.yaml).
#
# Keep the base image on the Strimzi and Kafka versions the cluster runs
# (gitops streaming/base/kafka.yaml). Downloads are pinned by checksum: a
# connector runs with the worker's Kafka identity and AWS role.

# ---- Unpack the plugin ----
FROM alpine:3.24.3 AS plugins

ADD --checksum=sha256:85661c4d3d49b85f4a65170a5c27464e4359760aa7140a24628e45323b6d7329 https://github.com/Aiven-Open/cloud-storage-connectors-for-apache-kafka/releases/download/v3.4.3/s3-sink-connector-for-apache-kafka-3.4.3.tar /tmp/s3-sink.tar
RUN mkdir -p /plugins/aiven-s3-sink && tar -xf /tmp/s3-sink.tar -C /plugins/aiven-s3-sink

# ---- Kafka ----
FROM quay.io/strimzi/kafka:1.3.0-kafka-4.3.1

# Strimzi's plugin.path: each directory under it is one plugin, loaded in a
# class loader of its own.
COPY --from=plugins /plugins/ /opt/kafka/plugins/

# On Kafka's own classpath, as AWS documents for its clients. Its Jackson is
# shaded and it brings no logging binding, so it doesn't clash with Kafka's.
ADD --chmod=644 --checksum=sha256:922064f312ddc8a5f3dbe65fcef3d02ddbd588a02e3a6f0fca4b5a4dbec1ba43 https://github.com/aws/aws-msk-iam-auth/releases/download/v2.3.9/aws-msk-iam-auth-2.3.9-all.jar /opt/kafka/libs/aws-msk-iam-auth-2.3.9-all.jar

# The base image's non-root user, stated so nothing above can change it.
USER 1001
