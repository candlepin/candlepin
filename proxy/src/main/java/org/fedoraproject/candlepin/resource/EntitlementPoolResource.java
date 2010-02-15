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

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;

/**
 * API gateway for the EntitlementPool
 */
@Path("/entitlementpool")
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
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<EntitlementPool> list() {
        return entitlementPoolCurator.findAll();
    }

    /**
     * Returns all the entitlement pools for the consumer with the given uuid.
     * @param consumerUuid whose entitlement pools are sought.
     * @return all the entitlement pools for the consumer with the given uuid.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/consumer/{consumer_uuid}")
    public List<EntitlementPool> listByConsumer(
            @PathParam("consumer_uuid") String consumerUuid) {

        // FIXME: not correct, we need to filter on only those
        // owned by the Consumer
        log.debug("listByConsumer, consumer_uuid is: " + consumerUuid);
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        log.debug("consumer is :" + consumer.toString());
        List<EntitlementPool>  eps = entitlementPoolCurator.listByConsumer(consumer);
        log.debug("EntitlementPools: " + eps.toString());
        return eps;
    }
    
}
