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

import org.candlepin.audit.EventAdapter;

import com.google.inject.Inject;

import org.jboss.resteasy.plugins.providers.atom.Feed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

/**
 * Resource exposing an Atom feed of Candlepin events.
 */
@Path("/atom")
@Api(value = "atom", authorizations = { @Authorization("basic") })
public class AtomFeedResource {

    private EventAdapter adapter;
    @Inject
    public AtomFeedResource(EventAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * Retrieves an Event Atom Feed.
     *
     * @deprecated Event persistence/retrieval is being phased out. This endpoint currently returns
     * a feed without any entries, and will be removed on the next major release.
     *
     * @return an empty Atom Feed
     */
    @Deprecated
    @ApiOperation(
        notes = "Retrieves an Event Atom Feed. DEPRECATED: Event persistence/retrieval is " +
        "being phased out. This endpoint currently returns a feed without any entries, and will be " +
        "removed on the next major release.",
        value = "getFeed")
    @GET
    @Produces({"application/atom+xml", MediaType.APPLICATION_JSON})
    public Feed getFeed() {
        Feed feed = this.adapter.toFeed(null, "/atom");
        feed.setTitle("Event Feed");
        return feed;
    }
}
