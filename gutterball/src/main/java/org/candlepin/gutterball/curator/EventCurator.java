package org.candlepin.gutterball.curator;

import org.candlepin.gutterball.model.Event;

import com.google.inject.Inject;
import com.mongodb.DB;

public class EventCurator extends MongoDBCurator<Event> {
	public static final String COLLECTION = "events";
	
	@Inject
	public EventCurator(DB database) {
		super(Event.class, database);
	}

	@Override
	public String getCollectionName() {
		return COLLECTION;
	}

}
