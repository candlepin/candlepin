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

import java.util.Date;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;


import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.model.EventCurator;
import org.jboss.resteasy.plugins.providers.atom.Content;
import org.jboss.resteasy.plugins.providers.atom.Entry;
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

    @Inject
    public AtomFeedResource(EventCurator eventCurator) {
        this.eventCurator = eventCurator;
    }

    @GET
    @Produces("application/atom+xml")
    public Feed getFeed() {

        Feed feed = new Feed();
        feed.setUpdated(new Date());

        List<Event> events = eventCurator.listMostRecent(ATOM_FEED_LIMIT);
        for (Event e : events) {
            Entry entry = new Entry();
            entry.setTitle(e.getTarget().toString() + " " + e.getType().toString());
            entry.setPublished(e.getTimestamp());

            Content content = new Content();
            content.setJAXBObject(e);
            entry.setContent(content);
            feed.getEntries().add(entry);
        }

        // Use the most recent event as the feed's published time. Assumes events do not
        // get modified, if they do then the feed published date could be inaccurate.
        if (events.size() > 0) {
            feed.setUpdated(events.get(0).getTimestamp());
        }

        return feed;
    }

}
