package org.candlepin.event;

import javax.inject.Inject;

import org.candlepin.model.EventCurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    private static final int THREAD_COUNT = 3;

    private EventCurator eventCurator;

    @Inject
    public EventProcessor(EventCurator eventCurator) {
        this.eventCurator = eventCurator;
    }

}
