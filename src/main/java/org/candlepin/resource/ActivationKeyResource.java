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

import org.candlepin.auth.interceptor.Verify;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Pool;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.util.ServiceLevelValidator;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.DELETE;
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
    private static Logger log = LoggerFactory.getLogger(ActivationKeyResource.class);
    private ActivationKeyCurator activationKeyCurator;
    private PoolManager poolManager;
    private I18n i18n;
    private ServiceLevelValidator serviceLevelValidator;
    private ActivationKeyRules activationKeyRules;

    @Inject
    public ActivationKeyResource(ActivationKeyCurator activationKeyCurator,
        I18n i18n, PoolManager poolManager,
        ServiceLevelValidator serviceLevelValidator,
        ActivationKeyRules activationKeyRules) {
        this.activationKeyCurator = activationKeyCurator;
        this.i18n = i18n;
        this.poolManager = poolManager;
        this.serviceLevelValidator = serviceLevelValidator;
        this.activationKeyRules = activationKeyRules;
    }

    /**
     * Retrieves a single Activation Key
     * <p>
     * <pre>
     * {
     *   "id" : "database_id",
     *   "name" : "default_key",
     *   "owner" : {},
     *   "pools" : [ ],
     *   "contentOverrides" : [ ],
     *   "releaseVer" : {},
      *  "serviceLevel" : null,
      *  "updated" : [date]
     *   "created" : [date],
     * }
     * </pre>
     *
     * @return an ActivationKey object
     * @httpcode 400
     * @httpcode 200
     */
    @GET
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKey getActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);

        return key;
    }

    /**
     * Retrieves a list of Pools based on the Activation Key
     *
     * @return a list of Pool objects
     * @httpcode 400
     * @httpcode 200
     */
    @GET
    @Path("{activation_key_id}/pools")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Pool> getActivationKeyPools(
        @PathParam("activation_key_id") String activationKeyId) {
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        List<Pool> pools = new ArrayList<Pool>();
        for (ActivationKeyPool akp : key.getPools()) {
            pools.add(akp.getPool());
        }
        return pools;
    }

    /**
     * Updates an Activation Key
     *
     * @return an ActivationKey object
     * @httpcode 400
     * @httpcode 200
     */
    @PUT
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKey updateActivationKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        ActivationKey key) {
        ActivationKey toUpdate = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        if (key.getName() != null) {
            toUpdate.setName(key.getName());
        }
        String serviceLevel = key.getServiceLevel();
        if (serviceLevel != null) {
            serviceLevelValidator.validate(toUpdate.getOwner(), serviceLevel);
            toUpdate.setServiceLevel(serviceLevel);
        }
        if (key.getReleaseVer() != null) {
            toUpdate.setReleaseVer(key.getReleaseVer());
        }
        activationKeyCurator.merge(toUpdate);

        return toUpdate;
    }

    /**
     * Adds a Pool to an Activation Key
     *
     * @return a Pool object
     * @httpcode 400
     * @httpcode 200
     */
    @POST
    @Path("{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pool addPoolToKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        @PathParam("pool_id") @Verify(Pool.class) String poolId,
        @QueryParam("quantity") Long quantity) {

        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        Pool pool = findPool(poolId);

        // Throws a BadRequestException if adding pool to key is a bad idea
        activationKeyRules.validatePoolForActKey(key, pool, quantity);
        key.addPool(pool, quantity);
        activationKeyCurator.update(key);
        return pool;
    }

    /**
     * Removes a Pool from an Activation Key
     *
     * @return a Pool object
     * @httpcode 400
     * @httpcode 200
     */
    @DELETE
    @Path("{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pool removePoolFromKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        @PathParam("pool_id")
        @Verify(Pool.class) String poolId) {
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        Pool pool = findPool(poolId);
        key.removePool(pool);
        activationKeyCurator.update(key);
        return pool;
    }

    /**
     * Retrieves a list of Activation Keys
     *
     * @return a list of ActivationKey objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ActivationKey> findActivationKey() {
        List<ActivationKey> keyList = activationKeyCurator.listAll();
        return keyList;
    }

    /**
     * Removes an Activation Key
     *
     * @httpcode 400
     * @httpcode 200
     */
    @DELETE
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);

        log.debug("Deleting info " + activationKeyId);

        activationKeyCurator.delete(key);
    }

    private Pool findPool(String poolId) {
        Pool pool = poolManager.find(poolId);

        if (pool == null) {
            throw new BadRequestException(i18n.tr(
                "Pool with id {0} could not be found.", poolId));
        }
        return pool;
    }
}
