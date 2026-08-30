package com.transactionaloutbox.library;

import com.transactionaloutbox.library.dispatcher.OutboxDispatcher;
import com.transactionaloutbox.library.repository.OutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class Outbox {

    private final OutboxRepository outboxRepository;
    private final OutboxDispatcher dispatcher;
    private final AfterCommitWakeup afterCommitWakeup;

    public Outbox(OutboxRepository outboxRepository, OutboxDispatcher dispatcher, AfterCommitWakeup afterCommitWakeup) {
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
        this.afterCommitWakeup = afterCommitWakeup;
    }

    /**
     * Stages a message for outbox dispatch and wakes the dispatcher after the
     * surrounding transaction commits. The wakeup only reaches the dispatcher
     * within this JVM: if a non-leader replica accepts the write, the leader
     * only learns about the new row on its next poll cycle (default 500ms) —
     * there is no cross-replica wakeup propagation.
     *
     * @throws IllegalStateException if there is no active Spring transaction;
     *                               without one, the business update and outbox
     *                               insert cannot be atomic
     */
    public void stage(String type, Object payload) {
        if (transactionIsNotActive()) {
            throw new IllegalStateException("An active transaction is required to stage an outbox message");
        }
        outboxRepository.add(type, payload);
        afterCommitWakeup.triggerAsync(dispatcher::publishPendingMessages);
    }

    private boolean transactionIsNotActive() {
        return !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive();
    }
}
