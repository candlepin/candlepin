package org.candlepin.controller;

public class EventCreationException extends Exception {

    public EventCreationException() {
        super();
    }

    public EventCreationException(String message) {
        super(message);
    }

    public EventCreationException(Throwable cause) {
        super(cause);
    }
}
