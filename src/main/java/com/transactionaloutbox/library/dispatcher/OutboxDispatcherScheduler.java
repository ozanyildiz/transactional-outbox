package com.transactionaloutbox.library.dispatcher;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcherScheduler {

    private final OutboxDispatcher dispatcher;

    public OutboxDispatcherScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${outbox.dispatcher.poll-interval-ms:500}")
    public void poll() {
        dispatcher.publishPendingMessages();
    }
}
