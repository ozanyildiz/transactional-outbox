package com.transactionaloutbox.library.dispatcher;

public class OutboxDispatchException extends RuntimeException {

    public OutboxDispatchException(String message) {
        super(message);
    }

    public OutboxDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
