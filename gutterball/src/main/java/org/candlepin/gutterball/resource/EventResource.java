package org.candlepin.gutterball.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.candlepin.gutterball.curator.EventCurator;
import org.candlepin.gutterball.model.Event;

import com.google.inject.Inject;
import com.mongodb.DBCursor;

@Path("events")
public class EventResource {

	private EventCurator eventCurator;

	@Inject
	public EventResource(EventCurator eventCurator) {
		this.eventCurator = eventCurator;
	}
	
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public List<Map> getEvents() {
		List<Map> events = new ArrayList<Map>();
		DBCursor cursor = eventCurator.all();
		try {
			while(cursor.hasNext()) {
				Event next = (Event) cursor.next();
				events.add(next);
			}
		}
		finally {
			cursor.close();
		}
		return events;
	}
	
}
