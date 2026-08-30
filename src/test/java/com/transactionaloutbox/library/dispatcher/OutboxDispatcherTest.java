package com.transactionaloutbox.library.dispatcher;

import com.transactionaloutbox.library.leadership.LeadershipProvider;
import com.transactionaloutbox.library.model.OutboxMessage;
import com.transactionaloutbox.library.publisher.MessagePublisher;
import com.transactionaloutbox.library.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {

    private OutboxRepository repository;
    private MessagePublisher publisher;
    private LeadershipProvider leadershipProvider;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        publisher = mock(MessagePublisher.class);
        leadershipProvider = mock(LeadershipProvider.class);
        dispatcher = new OutboxDispatcher(repository, publisher, leadershipProvider);
        when(leadershipProvider.isLeader()).thenReturn(true);
    }

    private static OutboxMessage aMessage() {
        return new OutboxMessage(UUID.randomUUID(), "subject", "payload", Instant.now());
    }

    @Test
    void publishesNothingWhenNoMessageIsPending() {
        when(repository.findOldestPending()).thenReturn(Optional.empty());

        dispatcher.publishPendingMessages();

        verify(publisher, never()).publish(any());
        verify(repository, never()).markPublished(any());
    }

    @Test
    void publishesThenMarksPublished() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message), Optional.empty());

        dispatcher.publishPendingMessages();

        InOrder order = inOrder(publisher, repository);
        order.verify(publisher).publish(message);
        order.verify(repository).markPublished(message.id());
    }

    @Test
    void marksThePublishedMessageById() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message), Optional.empty());

        dispatcher.publishPendingMessages();

        verify(repository).markPublished(message.id());
    }

    @Test
    void doesNotMarkPublishedWhenPublishingFails() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message));
        doThrow(new RuntimeException("nats down")).when(publisher).publish(message);

        dispatcher.publishPendingMessages();

        verify(repository, never()).markPublished(any());
    }

    @Test
    void stopsDrainingWhenPublishingFails() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message));
        doThrow(new RuntimeException("boom")).when(publisher).publish(message);

        dispatcher.publishPendingMessages();

        verify(repository).findOldestPending();
    }

    @Test
    void stopsDrainingWhenMarkPublishedFails() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message));
        OutboxDispatchException markFailure = new OutboxDispatchException("could not mark published");
        doThrow(markFailure).when(repository).markPublished(message.id());

        dispatcher.publishPendingMessages();

        verify(publisher).publish(message);
        verify(repository).findOldestPending();
    }

    @Test
    void publishPendingMessagesDrainsWhenLeader() {
        when(leadershipProvider.isLeader()).thenReturn(true);
        when(repository.findOldestPending())
                .thenReturn(Optional.of(aMessage()), Optional.of(aMessage()), Optional.empty());

        dispatcher.publishPendingMessages();

        verify(repository, times(3)).findOldestPending();
        verify(publisher, times(2)).publish(any());
    }

    @Test
    void publishPendingMessagesSkipsDispatchWhenNotLeader() {
        when(leadershipProvider.isLeader()).thenReturn(false);

        dispatcher.publishPendingMessages();

        verify(repository, never()).findOldestPending();
    }

    @Test
    void publishPendingMessagesStopsDrainingAsSoonAsLeadershipIsLost() {
        // isLeader() is checked once before acquiring the lock, then once per
        // loop iteration: true (pre-lock), true, true (2 dispatches), false (stop).
        when(leadershipProvider.isLeader()).thenReturn(true, true, true, false);
        when(repository.findOldestPending()).thenReturn(Optional.of(aMessage()), Optional.of(aMessage()));

        dispatcher.publishPendingMessages();

        verify(repository, times(2)).findOldestPending();
        verify(publisher, times(2)).publish(any());
    }
}
