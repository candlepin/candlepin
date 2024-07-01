package org.candlepin.service.model;

public interface Event {

    public String getBody();

    public EventType getEventType();

}
