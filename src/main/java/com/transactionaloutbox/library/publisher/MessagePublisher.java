package com.transactionaloutbox.library.publisher;

import com.transactionaloutbox.library.model.OutboxMessage;

public interface MessagePublisher {

    void publish(OutboxMessage message);
}
