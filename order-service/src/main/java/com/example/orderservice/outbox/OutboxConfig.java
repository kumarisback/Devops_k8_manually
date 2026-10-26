package com.example.orderservice.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;

/** What the outbox needs where events are on: transactions, a schedule for the relay, and indexes. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class OutboxConfig {

    private final MongoTemplate mongo;

    public OutboxConfig(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    TransactionTemplate transactionTemplate(MongoTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // The relay looks for pending events due now, oldest first; sent events
    // are deleted by MongoDB a week after they went out.
    @EventListener(ApplicationReadyEvent.class)
    void ensureIndexes() {
        var indexes = mongo.indexOps(OutboxEvent.class);
        indexes.ensureIndex(new Index().on("status", Sort.Direction.ASC).on("nextAttemptAt", Sort.Direction.ASC)
                .named("pending_due"));
        indexes.ensureIndex(new Index().on("sentAt", Sort.Direction.ASC).expire(Duration.ofDays(7)).named("sent_ttl"));
    }
}
