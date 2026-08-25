package com.transactionaloutbox.library.repository;

import com.transactionaloutbox.library.model.OutboxMessage;

import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository {

    void add(String type, Object payload);

    void markPublished(UUID id);

    Optional<OutboxMessage> findOldestPending();
}
