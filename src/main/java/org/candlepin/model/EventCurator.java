package org.candlepin.model;

import javax.inject.Singleton;

@Singleton
public class EventCurator extends AbstractHibernateCurator<Event> {

    public EventCurator() {
        super(Event.class);
    }

}

