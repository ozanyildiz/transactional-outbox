package com.transactionaloutbox.library;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AfterCommitWakeupTest {

    private final AfterCommitWakeup afterCommitWakeup = new AfterCommitWakeup();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withinTransactionDefersActionUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        Runnable action = mock(Runnable.class);

        afterCommitWakeup.trigger(action);

        verify(action, never()).run();

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();

        verify(action).run();
    }

    @Test
    void withoutTransactionRunsActionImmediately() {
        Runnable action = mock(Runnable.class);

        afterCommitWakeup.trigger(action);

        verify(action).run();
    }
}
