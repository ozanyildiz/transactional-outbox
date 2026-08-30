package com.transactionaloutbox.library;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AfterCommitWakeup {

    private final TaskExecutor taskExecutor;

    public AfterCommitWakeup(@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * Runs the given action asynchronously, on another thread, after the
     * surrounding transaction commits. If there is no active transaction,
     * the action is submitted immediately instead of waiting for a commit —
     * but it is still handed off to another thread rather than run on the
     * caller's thread.
     */
    public void triggerAsync(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutor.execute(action);
                }
            });
        } else {
            taskExecutor.execute(action);
        }
    }
}
