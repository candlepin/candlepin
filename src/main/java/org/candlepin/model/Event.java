package org.candlepin.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.candlepin.service.model.EventType;

@Entity
@Table(name = Event.DB_TABLE)
public class Event extends AbstractHibernateObject<Event> {

    public final static String DB_TABLE = "cp_events";

    @Id
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(length = 32)
    @NotNull
    private String type;

    private String body;

    public Event setId(String id) {
        this.id = id;
        return this;
    }

    @Override
    public String getId() {
        return id;
    }

    public Event setType(EventType type) {
        this.type = type.name();
        return this;
    }

    public EventType getType() {
        return EventType.valueOf(type);
    }

    public Event setBody(String body) {
        this.body = body;
        return this;
    }

    public String getBody() {
        return body;
    }

}
