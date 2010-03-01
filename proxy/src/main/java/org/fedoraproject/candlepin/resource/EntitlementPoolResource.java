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

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * API gateway for the EntitlementPool
 */
@Path("/pool")
public class EntitlementPoolResource {

    private static Logger log = Logger.getLogger(EntitlementPoolResource.class);

    private EntitlementPoolCurator entitlementPoolCurator;
    private ConsumerCurator consumerCurator;

    /**
     * default ctor
     * @param entitlementPoolCurator interact with the entitlement pools.
     * @param consumerCurator interact with the consumers.
     */
    @Inject
    public EntitlementPoolResource(EntitlementPoolCurator entitlementPoolCurator,
                                    ConsumerCurator consumerCurator) {
        this.entitlementPoolCurator = entitlementPoolCurator;
        this.consumerCurator = consumerCurator;
    }
   
    /**
     * Returns the list of available entitlement pools.
     * @return the list of available entitlement pools.
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Pools list() {
        Pools returnValue = new Pools();
        returnValue.pool = entitlementPoolCurator.findAll();
        return returnValue;
    }
    
    /**
     * Return the Entitlement Pool for the given id
     * 
     * @param id
     *            the id of the pool
     * @return the pool identified by the id
     */
    @GET
    @Path("/{pool_id}")    
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public EntitlementPool getProduct(@PathParam("pool_id") Long id) {
        EntitlementPool toReturn = entitlementPoolCurator.find(id);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException("Entitlement Pool with ID '" + id +
            "' could not be found");
    }    

    /**
     * Returns all the entitlement pools available to a consumer.
     *
     * @param consumerUuid Consumer requesting available entitlement pools.
     * @return all the entitlement pools for the consumer with the given uuid.
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Path("/consumer/{consumer_uuid}")
    public List<EntitlementPool> listByConsumer(
            @PathParam("consumer_uuid") String consumerUuid) {
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        List<EntitlementPool>  eps = entitlementPoolCurator.listAvailableEntitlementPools(
            consumer);
        return eps;
    }
    
}
