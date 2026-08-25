package com.transactionaloutbox.library.dispatcher;

import com.transactionaloutbox.library.model.OutboxMessage;
import com.transactionaloutbox.library.publisher.MessagePublisher;
import com.transactionaloutbox.library.repository.OutboxRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OutboxDispatcher {

    private final OutboxRepository repository;
    private final MessagePublisher publisher;

    public OutboxDispatcher(OutboxRepository repository, MessagePublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public boolean dispatchNext() {
        Optional<OutboxMessage> next = repository.findOldestPending();
        if (next.isEmpty()) {
            return false;
        }
        OutboxMessage message = next.get();

        try {
            publisher.publish(message);
        } catch (Exception e) {
            throw new OutboxDispatchException("Failed to publish outbox message " + message.id(), e);
        }

        repository.markPublished(message.id());
        return true;
    }
}
