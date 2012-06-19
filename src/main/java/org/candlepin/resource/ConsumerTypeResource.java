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
package org.candlepin.resource;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * Access Path for consumer types
 */
@Path("/consumertypes")
public class ConsumerTypeResource {
    private static Logger log = Logger.getLogger(ConsumerTypeResource.class);
    private ConsumerTypeCurator consumerTypeCurator;
    private I18n i18n;

    @Inject
    public ConsumerTypeResource(ConsumerTypeCurator consumerTypeCurator, I18n i18n) {
        this.consumerTypeCurator = consumerTypeCurator;
        this.i18n = i18n;
    }

    /**
     * List available ConsumerTypes
     *
     * @return all the consumer types
     * @httpcode 200
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON })
    @Wrapped(element = "consumertypes")
    public List<ConsumerType> list() {
        return consumerTypeCurator.listAll();
    }

    /**
     * Return the consumer type identified by the given label.
     *
     * @return a consumer type
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public ConsumerType getConsumerType(@PathParam("id") String id) {
        ConsumerType toReturn = consumerTypeCurator.find(id);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException(
            i18n.tr("ConsumerType with id '" + id + "' could not be found."));
    }

    /**
     * Create a ConsumerType
     *
     * @return newly created ConsumerType
     * @throws BadRequestException When the type is not found
     * @httpcode 400
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ConsumerType create(ConsumerType in) throws BadRequestException {
        try {
            ConsumerType toReturn = consumerTypeCurator.create(in);
            return toReturn;
        }
        catch (Exception e) {
            log.error("Problem creating consumertype:", e);
            throw new BadRequestException(
                i18n.tr("Problem creating consumertype: {0}", in));
        }
    }

    /**
     * Update a ConsumerType
     *
     * @return newly created ConsumerType
     * @throws BadRequestException When the type is not found
     * @httpcode 400
     * @httpcode 200
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ConsumerType update(ConsumerType in) throws BadRequestException {
        ConsumerType type = consumerTypeCurator.find(in.getId());

        if (type == null) {
            throw new BadRequestException(
                i18n.tr("Consumer Type with label {0} could not be found.", in.getId()));
        }

        consumerTypeCurator.merge(in);
        return in;
    }

    /**
     * Deletes a consumer type
     *
     * @httpcode 400
     * @httpcode 200
     */
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteConsumerType(@PathParam("id") String id) {
        ConsumerType type = consumerTypeCurator.find(id);

        if (type == null) {
            throw new BadRequestException(
                i18n.tr("Consumer Type with id {0} could not be found.", id));
        }

        consumerTypeCurator.delete(type);
    }
}
