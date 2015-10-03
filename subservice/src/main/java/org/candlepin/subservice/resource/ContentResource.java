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
package org.candlepin.subservice.resource;

import org.candlepin.model.Content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * The ContentResource class provides an API to add product content
 * service.
 */
@Path("/content")
public class ContentResource {
    private static Logger log = LoggerFactory.getLogger(ContentResource.class);
    Set<Content> contents = new HashSet<Content>();

    // Things we need:
    // Backing curator
    // Translation service (I18n)
    // Authentication

    /**
     * Creates a new content from the content JSON provided.
     *
     * @param content
     *  A Content object built from the JSON provided in the request
     *
     * @return
     *  The newly created Content object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Content createContent(Content content) {
        // TODO
        log.error("createContent:"+content);
        contents.add(content);
        return content;
    }

}
