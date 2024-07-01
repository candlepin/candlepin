package org.candlepin.service;

import org.candlepin.service.exception.EventPublishException;
import org.candlepin.service.model.Event;

public interface EventAdapter {

    public void publish(Event event) throws EventPublishException;

}
