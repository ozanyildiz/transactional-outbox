package com.transactionaloutbox.library.dispatcher;

import com.transactionaloutbox.library.leadership.LeadershipProvider;
import com.transactionaloutbox.library.model.OutboxMessage;
import com.transactionaloutbox.library.publisher.MessagePublisher;
import com.transactionaloutbox.library.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxRepository repository;
    private final MessagePublisher publisher;
    private final LeadershipProvider leadershipProvider;
    private final Lock dispatchLock = new ReentrantLock();

    public OutboxDispatcher(OutboxRepository repository,
                            MessagePublisher publisher,
                            LeadershipProvider leadershipProvider) {
        this.repository = repository;
        this.publisher = publisher;
        this.leadershipProvider = leadershipProvider;
    }

    /**
     * Best-effort: drains as many outbox messages as are currently pending.
     * A call may be a no-op (non-leader, or a drain already in progress);
     * the scheduled poll remains the correctness mechanism.
     */
    public void publishPendingMessages() {
        if (isNotLeaderOrDrainAlreadyInProgress()) {
            return;
        }
        try {
            while (leadershipProvider.isLeader()) {
                Optional<OutboxMessage> next = repository.findOldestPending();
                if (next.isEmpty()) {
                    return;
                }

                publishAndMark(next.get());
            }
        } catch (RuntimeException e) {
            log.warn("Outbox dispatch failed; will retry on next poll", e);
        } finally {
            dispatchLock.unlock();
        }
    }

    private boolean isNotLeaderOrDrainAlreadyInProgress() {
        return !leadershipProvider.isLeader() || !dispatchLock.tryLock();
    }

    private void publishAndMark(OutboxMessage message) {
        try {
            publisher.publish(message);
        } catch (Exception e) {
            throw new OutboxDispatchException("Failed to publish outbox message " + message.id(), e);
        }

        repository.markPublished(message.id());
    }
}
