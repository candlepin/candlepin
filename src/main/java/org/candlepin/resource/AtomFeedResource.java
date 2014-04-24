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
package org.candlepin.resource;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventAdapter;
import org.candlepin.model.EventCurator;

import com.google.inject.Inject;

import org.jboss.resteasy.plugins.providers.atom.Feed;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Resource exposing an Atom feed of Candlepin events.
 */
@Path("/atom")
public class AtomFeedResource {

    private static final int ATOM_FEED_LIMIT = 1000;

    private EventCurator eventCurator;
    private EventAdapter adapter;
    @Inject
    public AtomFeedResource(EventCurator eventCurator, EventAdapter adapter) {
        this.eventCurator = eventCurator;
        this.adapter = adapter;
    }

    /**
     * Retrieves an Event Atom Feed
     *
     * @return a Feed object
     * @httpcode 200
     */
    @GET
    @Produces({"application/atom+xml", MediaType.APPLICATION_JSON})
    public Feed getFeed() {
        List<Event> events = eventCurator.listMostRecent(ATOM_FEED_LIMIT);
        Feed feed = this.adapter.toFeed(events, "/atom");
        feed.setTitle("Event Feed");
        return feed;
    }
}
