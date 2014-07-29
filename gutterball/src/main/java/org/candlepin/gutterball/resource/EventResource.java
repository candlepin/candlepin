/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

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

/**
 * A potentially temporary resource that opens an API for checking our
 * Events.
 *
 */
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
            while (cursor.hasNext()) {
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
