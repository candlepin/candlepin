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

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventAdapter;
import org.fedoraproject.candlepin.model.EventCurator;
import org.jboss.resteasy.plugins.providers.atom.Feed;

import com.google.inject.Inject;

/**
 * Resource exposing an Atom feed of Candlepin events.
 */
@Path("/atom")
public class AtomFeedResource {

    private static Logger log = Logger.getLogger(AtomFeedResource.class);
    private static final int ATOM_FEED_LIMIT = 1000;

    private EventCurator eventCurator;
    private EventAdapter adapter;
    @Inject
    public AtomFeedResource(EventCurator eventCurator, EventAdapter adapter) {
        this.eventCurator = eventCurator;
        this.adapter = adapter;
    }

    @GET
    @Produces({"application/atom+xml", MediaType.APPLICATION_JSON})
    public Feed getFeed() {
        List<Event> events = eventCurator.listMostRecent(ATOM_FEED_LIMIT);
        return this.adapter.toFeed(events);
    }

}
