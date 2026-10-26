package com.example.orderservice.outbox;

import com.example.orderservice.model.Order;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionalOutboxOrderCreationTest {

    /** Records commits and rollbacks instead of talking to MongoDB. */
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        final List<String> log = new ArrayList<>();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            log.add("begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            log.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            log.add("rollback");
        }
    }

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final TransactionalOutboxOrderCreation creation = new TransactionalOutboxOrderCreation(mongo,
            new TransactionTemplate(transactions), Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void writesTheOrderAndItsEventInOneTransaction() {
        when(mongo.insert(any(Order.class))).thenAnswer(call -> {
            Order order = call.getArgument(0);
            order.setId("o-1");
            return order;
        });
        when(mongo.insert(any(OutboxEvent.class))).thenAnswer(call -> call.getArgument(0));

        Order order = creation.create("asha", "Kafka book", 42.5);

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(mongo).insert(event.capture());
        assertThat(order.getId()).isEqualTo("o-1");
        assertThat(event.getValue().getType()).isEqualTo("OrderCreated");
        assertThat(event.getValue().getAggregateId()).isEqualTo("o-1");
        assertThat(event.getValue().getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getValue().getPayload()).containsEntry("userId", "asha").containsEntry("amount", 42.5);
        assertThat(transactions.log).containsExactly("begin", "commit");
    }

    @Test
    void ifTheEventCannotBeStoredTheOrderIsRolledBack() {
        when(mongo.insert(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        when(mongo.insert(any(OutboxEvent.class))).thenThrow(new IllegalStateException("write conflict"));

        assertThatThrownBy(() -> creation.create("asha", "Kafka book", 42.5)).isInstanceOf(IllegalStateException.class);

        assertThat(transactions.log).containsExactly("begin", "rollback");
    }
}
