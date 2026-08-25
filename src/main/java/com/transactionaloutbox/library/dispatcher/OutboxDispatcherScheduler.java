package com.transactionaloutbox.library.dispatcher;

import com.transactionaloutbox.library.leadership.LeadershipProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


@Component
public class OutboxDispatcherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcherScheduler.class);

    private final OutboxDispatcher dispatcher;
    private final LeadershipProvider leadershipProvider;
    private final TaskExecutor taskExecutor;
    private final Lock dispatchLock = new ReentrantLock();

    public OutboxDispatcherScheduler(OutboxDispatcher dispatcher,
                                     LeadershipProvider leadershipProvider,
                                     @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.dispatcher = dispatcher;
        this.leadershipProvider = leadershipProvider;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Best-effort latency hint: runs a poll cycle off the caller's thread.
     * A wakeup may be dropped (non-leader, or a drain already in progress);
     * the scheduled poll remains the correctness mechanism.
     */
    public void wakeUp() {
        taskExecutor.execute(this::runIfIdle);
    }

    @Scheduled(fixedDelayString = "${outbox.dispatcher.poll-interval-ms:500}")
    public void poll() {
        runIfIdle();
    }

    private void runIfIdle() {
        if (!leadershipProvider.isLeader()) {
            return;
        }
        if (!dispatchLock.tryLock()) {
            return;
        }
        try {
            // Rechecked every iteration: StaticLeadershipProvider is a
            // config-driven single-replica stand-in for this take-home. In
            // production, leader election needs fencing (e.g. a lease with an
            // epoch/token) so two replicas can't both believe they're leader
            // during a handoff — dispatchLock only serializes drains within
            // this JVM, it does not coordinate across replicas.
            while (leadershipProvider.isLeader() && dispatcher.dispatchNext()) {
                // keep draining while still leader
            }
        } catch (RuntimeException e) {
            log.warn("Outbox dispatch failed; will retry on next poll", e);
        } finally {
            dispatchLock.unlock();
        }
    }
}
