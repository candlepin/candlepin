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
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;

import com.google.inject.Inject;

/**
 * Acess Path for consumer types
 */
@Path("/consumertypes")
public class ConsumerTypeResource {
    private static Logger log = Logger.getLogger(ConsumerTypeResource.class);
    private ConsumerTypeCurator consumerTypeCurator;

    @Inject
    public ConsumerTypeResource(ConsumerTypeCurator consumerTypeCurator) {
        this.consumerTypeCurator = consumerTypeCurator;
    }

    /**
     * List available ConsumerTypes
     * 
     * @return all the consumer types
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Wrapped(element = "consumertypes")    
    public List<ConsumerType> list() {
        return consumerTypeCurator.findAll();
    }

    /**
     * Return the consumer type identified by the given label.
     * 
     * @return a consumer type
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/{id}")
    public ConsumerType getConsumerType(@PathParam("id") Long id) {
        ConsumerType toReturn = consumerTypeCurator.find(id);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException("ConsumerType with id '" + id +
            "' could not be found");
    }

    /**
     * Create a ConsumerType
     * 
     * @return newly created ConsumerType
     * @throws BadRequestException When the type is not found
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ConsumerType create(ConsumerType in) throws BadRequestException {
        try {
            ConsumerType toReturn = consumerTypeCurator.create(in);
            return toReturn;
        }
        catch (Exception e) {
            log.error("Problem creating consumertype:", e);
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Update a ConsumerType
     * 
     * @return newly created ConsumerType
     * @throws BadRequestException When the type is not found
     *            
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ConsumerType update(ConsumerType in) throws BadRequestException {
        ConsumerType type = consumerTypeCurator.find(in.getId());

        if (type == null) {
            throw new BadRequestException("Consumer Type with label " +
                in.getId() + " could not be found");
        }

        consumerTypeCurator.merge(in);
        return in;
    }

    /**
     * Deletes a consumer type
     */
    @DELETE
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public void deleteConsumerType(@PathParam("id") Long id) {
        ConsumerType type = consumerTypeCurator.find(id);

        if (type == null) {
            throw new BadRequestException("Consumer Type with id " + id +
                " could not be found");
        }

        consumerTypeCurator.delete(type);
    }
}
