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
package org.fedoraproject.candlepin.audit;

import java.net.URI;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.jboss.resteasy.plugins.providers.atom.Content;
import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.atom.Link;
import org.jboss.resteasy.plugins.providers.atom.Person;

import com.google.inject.Inject;

/**
 * EventAdapterImpl
 */
public class EventAdapterImpl implements EventAdapter {
    
    private static final String URI_BASE = "http://fedorahosted.org";
    private static final String EVENTS_PATH = "/candlepin/events"; 
    private static final String URI = URI_BASE + EVENTS_PATH;
    
    private String candlepinURL;
    
    @Inject
    public EventAdapterImpl(Config config) {
        candlepinURL = config.getString(ConfigProperties.CANDLEPIN_URL) + EVENTS_PATH + "/";
    }
    
    @Override
    public Feed toFeed(List<Event> events) {
        Feed feed = new Feed();
        feed.setUpdated(new Date());
        feed.getAuthors().add(new Person("Red Hat, Inc."));
        try {
            feed.setId(new URI(URI));
        } 
        catch (Exception e) {
            // ignore, shouldn't happen
        }

        if (events == null) {
            return feed;
        }

        for (Event e : events) {
            Entry entry = new Entry();
            entry.setTitle(e.getTarget().toString() + " " + e.getType().toString());
            entry.setPublished(e.getTimestamp());
            entry.setUpdated(e.getTimestamp());
            entry.getAuthors().add(new Person("Red Hat, Inc."));
            URI eventURI = null;
            try {
                eventURI = new URI(candlepinURL + e.getId());
            }
            catch (Exception error) {
                // ignore, shouldn't happen
            }
            entry.setId(eventURI);
            entry.getLinks().add(
                new Link(
                    "alternate", 
                    eventURI,
                    MediaType.APPLICATION_JSON_TYPE));

            Content content = new Content();
            content.setType(MediaType.APPLICATION_XML_TYPE);
            content.setJAXBObject(e);
            entry.setContent(content);
            entry.setSummary(e.toString());
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
