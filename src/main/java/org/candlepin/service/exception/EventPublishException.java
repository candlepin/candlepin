package org.candlepin.service.exception;

public class EventPublishException extends Exception {
    public EventPublishException() {
        super();
    }

    public EventPublishException(String message) {
        super(message);
    }

    public EventPublishException(Throwable cause) {
        super(cause);
    }

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
