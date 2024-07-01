package org.candlepin.controller;

import javax.inject.Inject;

import org.candlepin.model.Event;
import org.candlepin.model.EventCurator;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EventManager {
    private static final Logger log = LoggerFactory.getLogger(EventManager.class);

    private EventAdapter adapter;
    private EventCurator eventCurator;
    private ObjectMapper mapper;

    @Inject
    public EventManager(EventAdapter adapter, EventCurator eventCurator, ObjectMapper mapper) {
        this.adapter = adapter;
        this.eventCurator = eventCurator;
        this.mapper = mapper;
    }

    public Event createEvent(EventType type, Object body) throws EventCreationException {
        if (type == null) {
            throw new IllegalArgumentException("event type is null");
        }

        if (body == null) {
            throw new IllegalArgumentException("event body is null");
        }

        Event event = new Event();
        event.setType(type);
        try {
            event.setBody(mapper.writeValueAsString(body));
        }
        catch(JsonProcessingException e) {
            throw new EventCreationException(e);
        }

        return eventCurator.create(event);
    }

}
