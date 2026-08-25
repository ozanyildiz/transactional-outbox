package com.transactionaloutbox.library.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
        UUID id,
        String subject,
        String payload,
        Instant createdAt) {
}
