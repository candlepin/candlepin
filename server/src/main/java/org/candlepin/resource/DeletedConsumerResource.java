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

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.resource.util.ResourceDateParser;

import com.google.inject.Inject;

/**
 * DeletedConsumerResource
 */
@Path("/deleted_consumers")
public class DeletedConsumerResource {
    private DeletedConsumerCurator deletedConsumerCurator;

    @Inject
    public DeletedConsumerResource(DeletedConsumerCurator deletedConsumerCurator) {
        this.deletedConsumerCurator = deletedConsumerCurator;
    }

    /**
     * Retrieves a list of Deleted Consumers
     * <p>
     * By deletion date or all. List returned is the deleted Consumers.
     *
     * @return a list of Consumer objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<DeletedConsumer> listByDate(@QueryParam("date") String dateStr) {
        if (dateStr != null) {
            return deletedConsumerCurator.findByDate(
                    ResourceDateParser.parseDateString(dateStr));
        }
        else {
            return deletedConsumerCurator.listAll();
        }
    }
}
