package com.transactionaloutbox.library;

import com.transactionaloutbox.library.dispatcher.OutboxDispatcherScheduler;
import com.transactionaloutbox.library.repository.OutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class Outbox {

    private final OutboxRepository outboxRepository;
    private final OutboxDispatcherScheduler scheduler;
    private final AfterCommitWakeup afterCommitWakeup;

    public Outbox(OutboxRepository outboxRepository, OutboxDispatcherScheduler scheduler, AfterCommitWakeup afterCommitWakeup) {
        this.outboxRepository = outboxRepository;
        this.scheduler = scheduler;
        this.afterCommitWakeup = afterCommitWakeup;
    }

    /**
     * Stages a message for outbox dispatch and wakes the dispatcher after the
     * surrounding transaction commits. The wakeup only reaches the scheduler
     * within this JVM: if a non-leader replica accepts the write, the leader
     * only learns about the new row on its next poll cycle (default 500ms) —
     * there is no cross-replica wakeup propagation.
     *
     * @throws IllegalStateException if there is no active Spring transaction;
     *                               without one, the business update and outbox
     *                               insert cannot be atomic
     */
    public void stage(String type, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("An active transaction is required to stage an outbox message");
        }
        outboxRepository.add(type, payload);
        afterCommitWakeup.trigger(scheduler::wakeUp);
    }
}
