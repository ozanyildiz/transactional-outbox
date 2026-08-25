package com.transactionaloutbox.library.dispatcher;

import com.transactionaloutbox.library.leadership.LeadershipProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherSchedulerTest {

    private OutboxDispatcher dispatcher;
    private LeadershipProvider leadershipProvider;
    private OutboxDispatcherScheduler scheduler;

    @BeforeEach
    void setUp() {
        dispatcher = mock(OutboxDispatcher.class);
        leadershipProvider = mock(LeadershipProvider.class);
        scheduler = new OutboxDispatcherScheduler(dispatcher, leadershipProvider, Runnable::run);
    }

    @Test
    void wakeUpDrainsWhenLeader() {
        when(leadershipProvider.isLeader()).thenReturn(true);
        when(dispatcher.dispatchNext()).thenReturn(true, true, false);

        scheduler.wakeUp();

        verify(dispatcher, times(3)).dispatchNext();
    }

    @Test
    void wakeUpSkipsDispatchWhenNotLeader() {
        when(leadershipProvider.isLeader()).thenReturn(false);

        scheduler.wakeUp();

        verify(dispatcher, never()).dispatchNext();
    }

    @Test
    void wakeUpStopsDrainingAsSoonAsLeadershipIsLost() {
        // isLeader() is checked once before acquiring the lock, then once per
        // loop iteration: true (pre-lock), true, true (2 dispatches), false (stop).
        when(leadershipProvider.isLeader()).thenReturn(true, true, true, false);
        when(dispatcher.dispatchNext()).thenReturn(true, true, true);

        scheduler.wakeUp();

        verify(dispatcher, times(2)).dispatchNext();
    }
}
