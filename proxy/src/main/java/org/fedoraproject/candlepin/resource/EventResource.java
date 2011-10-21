/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource;

import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventAdapter;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.EventCurator;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Candlepin Events Resource
 */
@Path("/events")
public class EventResource {

    private EventCurator eventCurator;
    private I18n i18n;
    private EventAdapter eventAdapter;


    @Inject
    public EventResource(EventCurator eventCurator, I18n i18n, EventAdapter eventAdapter) {
        this.eventCurator = eventCurator;
        this.i18n = i18n;
        this.eventAdapter = eventAdapter;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Event> listEvents() {
        List<Event> events = eventCurator.listAll();
        if (events != null) {
            eventAdapter.addMessageText(events);
        }
        return events;
    }

    @GET
    @Path("{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Event getEvent(@PathParam("uuid") String uuid) {
        Event toReturn = eventCurator.find(uuid);
        if (toReturn != null) {
            ArrayList<Event> events = new ArrayList<Event>();
            events.add(toReturn);
            eventAdapter.addMessageText(events);
            return toReturn;
        }

        throw new NotFoundException(i18n.tr(
            "Event with ID ''{0}'' could not be found.", uuid));
    }
}
