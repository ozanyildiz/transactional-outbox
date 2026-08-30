package com.transactionaloutbox.library.dispatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxDispatcherSchedulerTest {

    private OutboxDispatcher dispatcher;
    private OutboxDispatcherScheduler scheduler;

    @BeforeEach
    void setUp() {
        dispatcher = mock(OutboxDispatcher.class);
        scheduler = new OutboxDispatcherScheduler(dispatcher);
    }

    @Test
    void pollPublishesPendingMessages() {
        scheduler.poll();

        verify(dispatcher).publishPendingMessages();
    }
}
