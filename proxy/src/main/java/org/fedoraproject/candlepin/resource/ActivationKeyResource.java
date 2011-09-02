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

import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.interceptor.Verify;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.ActivationKey;
import org.fedoraproject.candlepin.model.ActivationKeyCurator;
import org.fedoraproject.candlepin.model.ActivationKeyPool;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.ProductPoolAttribute;

import com.google.inject.Inject;

import org.apache.log4j.Logger;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * SubscriptionTokenResource
 */
@Path("/activation_keys")
public class ActivationKeyResource {
    private static Logger log = Logger.getLogger(ActivationKeyResource.class);
    private ActivationKeyCurator activationKeyCurator;
    private PoolCurator poolCurator;
    private I18n i18n;
    private EventSink eventSink;

    @Inject
    public ActivationKeyResource(ActivationKeyCurator activationKeyCurator,
        I18n i18n, PoolCurator poolCurator, ConsumerResource consumerResource,
        EventSink eventSink) {
        this.activationKeyCurator = activationKeyCurator;
        this.i18n = i18n;
        this.poolCurator = poolCurator;
        this.eventSink = eventSink;
    }

    @GET
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKey getActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = findKey(activationKeyId);

        return key;
    }

    @GET
    @Path("{activation_key_id}/pools")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Pool> getActivationKeyPools(
        @PathParam("activation_key_id") String activationKeyId) {
        ActivationKey key = findKey(activationKeyId);
        List<Pool> pools = new ArrayList<Pool>();
        for (ActivationKeyPool akp : key.getPools()) {
            pools.add(akp.getPool());
        }
        return pools;
    }

    @PUT
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKey updateActivationKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        ActivationKey key) {
        ActivationKey toUpdate = findKey(activationKeyId);
        toUpdate.setName(key.getName());
        activationKeyCurator.merge(toUpdate);

        return toUpdate;
    }

    @POST
    @Path("{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pool addPoolToKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        @PathParam("pool_id")
        @Verify(value = Pool.class, require = Access.READ_POOLS) String poolId,
        @QueryParam("quantity") @DefaultValue("1") long quantity) {

        if (quantity < 1) {
            throw new BadRequestException(
                i18n.tr("The quantity must be greater than 0"));
        }
        ActivationKey key = findKey(activationKeyId);
        Pool pool = findPool(poolId);
        
        if(pool.getAttributeValue("requires_consumer_type") != null &&
            pool.getAttributeValue("requires_consumer_type").equals("person") || 
            pool.getProductAttribute("requires_consumer_type") != null &&
            pool.getProductAttribute("requires_consumer_type").getValue()
                  .equals("person")) {
            throw new BadRequestException(i18n.tr("Pools requiring a 'person' " +
                "consumer should not be added to an activation key since a " +
                "consumer type of 'person' cannot be used with activation " +
                "keys"));
        }
        if (quantity > 1) {
            ProductPoolAttribute ppa = pool.getProductAttribute("multi-entitlement");
            if (ppa == null || !ppa.getValue().equalsIgnoreCase("yes")) {
                throw new BadRequestException(
                    i18n.tr("Error: Only pools with multi-entitlement product" +
                        " subscriptions can be added to the activation key with" +
                        " a quantity greater than one."));
            }
        }
        if (quantity > pool.getQuantity()) {
            throw new BadRequestException(
                i18n.tr("The quantity must not be greater than the total " +
                    "allowed for the pool"));                        
        }        
        key.addPool(pool, quantity);
        activationKeyCurator.update(key);
        return pool;
    }

    @DELETE
    @Path("{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pool removePoolFromKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        @PathParam("pool_id")
        @Verify(value = Pool.class, require = Access.READ_POOLS) String poolId) {
        ActivationKey key = findKey(activationKeyId);
        Pool pool = findPool(poolId);
        key.removePool(pool);
        activationKeyCurator.update(key);
        return pool;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ActivationKey> findActivationKey() {
        List<ActivationKey> keyList = activationKeyCurator.listAll();
        return keyList;
    }

    @DELETE
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = findKey(activationKeyId);

        log.debug("Deleting info " + activationKeyId);

        activationKeyCurator.delete(key);
    }
    
    private ActivationKey findKey(String activationKeyId) {
        ActivationKey key = activationKeyCurator
        .find(activationKeyId);

        if (key == null) {
            throw new BadRequestException(
                i18n.tr("ActivationKey with id {0} could not be found",
                    activationKeyId));
        }
        return key;
    }

    private Pool findPool(String poolId) {
        Pool pool = poolCurator.find(poolId);

        if (pool == null) {
            throw new BadRequestException(i18n.tr(
                "Pool with id {0} could not be found", poolId));
        }
        return pool;
    }
}
