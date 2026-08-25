package com.transactionaloutbox.library.publisher;

import com.transactionaloutbox.library.model.OutboxMessage;

public interface MessagePublisher {

    /**
     * Publishes the message and must block until the broker has acknowledged
     * it. The dispatcher only marks a message published after this call
     * returns successfully.
     */
    void publish(OutboxMessage message);
}
