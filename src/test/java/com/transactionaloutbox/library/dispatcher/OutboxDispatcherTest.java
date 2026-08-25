package com.transactionaloutbox.library.dispatcher;

import com.transactionaloutbox.library.model.OutboxMessage;
import com.transactionaloutbox.library.publisher.MessagePublisher;
import com.transactionaloutbox.library.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {

    private OutboxRepository repository;
    private MessagePublisher publisher;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        publisher = mock(MessagePublisher.class);
        dispatcher = new OutboxDispatcher(repository, publisher);
    }

    private static OutboxMessage aMessage() {
        return new OutboxMessage(UUID.randomUUID(), "subject", "payload", Instant.now());
    }

    @Test
    void returnsFalseAndPublishesNothingWhenNoMessageIsPending() {
        when(repository.findOldestPending()).thenReturn(Optional.empty());

        boolean result = dispatcher.dispatchNext();

        assertThat(result).isFalse();
        verify(publisher, never()).publish(any());
        verify(repository, never()).markPublished(any());
    }

    @Test
    void publishesThenMarksPublished() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message));

        boolean result = dispatcher.dispatchNext();

        assertThat(result).isTrue();
        InOrder order = inOrder(publisher, repository);
        order.verify(publisher).publish(message);
        order.verify(repository).markPublished(message.id());
    }

    @Test
    void marksThePublishedMessageById() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message));

        dispatcher.dispatchNext();

        verify(repository).markPublished(message.id());
    }

    @Test
    void doesNotMarkPublishedWhenPublishingFails() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message));
        doThrow(new RuntimeException("nats down")).when(publisher).publish(message);

        assertThatThrownBy(() -> dispatcher.dispatchNext())
                .isInstanceOf(OutboxDispatchException.class)
                .hasMessageContaining(message.id().toString())
                .hasCauseInstanceOf(RuntimeException.class);

        verify(repository, never()).markPublished(any());
    }

    @Test
    void wrapsPublisherExceptionInOutboxDispatchException() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message));
        RuntimeException cause = new RuntimeException("boom");
        doThrow(cause).when(publisher).publish(message);

        assertThatThrownBy(() -> dispatcher.dispatchNext())
                .isInstanceOf(OutboxDispatchException.class)
                .hasCause(cause);
    }

    @Test
    void propagatesMarkPublishedFailureUncaught() {
        OutboxMessage message = aMessage();
        when(repository.findOldestPending()).thenReturn(Optional.of(message));
        OutboxDispatchException markFailure = new OutboxDispatchException("could not mark published");
        doThrow(markFailure).when(repository).markPublished(message.id());

        assertThatThrownBy(() -> dispatcher.dispatchNext())
                .isSameAs(markFailure);

        verify(publisher).publish(message);
    }
}
