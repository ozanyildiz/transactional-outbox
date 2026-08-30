package com.transactionaloutbox.library;

import com.transactionaloutbox.library.dispatcher.OutboxDispatcher;
import com.transactionaloutbox.library.repository.OutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OutboxTest {

    private OutboxRepository repository;
    private OutboxDispatcher dispatcher;
    private Outbox outbox;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        dispatcher = mock(OutboxDispatcher.class);
        outbox = new Outbox(repository, dispatcher, new AfterCommitWakeup(Runnable::run));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @Test
    void addsMessageToOutboxTable() {
        beginTransaction();
        Object payload = new Object();

        outbox.stage("payment.requested", payload);

        verify(repository).add("payment.requested", payload);
    }

    @Test
    void withinTransactionDefersPublishingUntilAfterCommit() {
        beginTransaction();

        outbox.stage("payment.requested", new Object());

        verify(dispatcher, never()).publishPendingMessages();

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();

        verify(dispatcher).publishPendingMessages();
    }

    @Test
    void withoutTransactionRejectsTheMessage() {
        Object payload = new Object();

        assertThatThrownBy(() -> outbox.stage("payment.requested", payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");

        verify(repository, never()).add("payment.requested", payload);
        verify(dispatcher, never()).publishPendingMessages();
    }
}
