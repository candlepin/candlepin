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
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.util.ServiceLevelValidator;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
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

    @Inject
    public ActivationKeyResource(ActivationKeyCurator activationKeyCurator,
        I18n i18n, PoolManager poolManager,
        ServiceLevelValidator serviceLevelValidator) {
        this.activationKeyCurator = activationKeyCurator;
        this.i18n = i18n;
        this.poolManager = poolManager;
        this.serviceLevelValidator = serviceLevelValidator;
    }

    /**
     * @return an ActivationKey
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
     * @return an ActivationKey
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
        toUpdate.setName(key.getName());
        String serviceLevel = key.getServiceLevel();
        if (serviceLevel != null) {
            serviceLevelValidator.validate(toUpdate.getOwner(), serviceLevel);
            toUpdate.setServiceLevel(serviceLevel);
        }
        Release release = key.getReleaseVer();
        if (release != null && release.getReleaseVer() != null) {
            if (release.getReleaseVer().length() >
                    ActivationKey.RELEASE_VERSION_LENGTH) {
                throw new BadRequestException(i18n.tr(
                    "Release version must not exceed {0} characters",
                    ActivationKey.RELEASE_VERSION_LENGTH));
            }
            toUpdate.setReleaseVer(key.getReleaseVer());
        }
        activationKeyCurator.merge(toUpdate);

        return toUpdate;
    }

    /**
     * @return a Pool
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

        if (quantity != null && quantity < 1) {
            throw new BadRequestException(
                i18n.tr("The quantity must be greater than 0"));
        }
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        Pool pool = findPool(poolId);

        if (pool.getAttributeValue("requires_consumer_type") != null &&
            pool.getAttributeValue("requires_consumer_type").equals("person") ||
            pool.getProductAttribute("requires_consumer_type") != null &&
            pool.getProductAttribute("requires_consumer_type").getValue()
                  .equals("person")) {
            throw new BadRequestException(i18n.tr("Cannot add pools that are " +
                    "restricted to unit type 'person' to activation keys."));
        }
        if (quantity != null && quantity > 1) {
            ProductPoolAttribute ppa = pool.getProductAttribute("multi-entitlement");
            if (ppa == null || !ppa.getValue().equalsIgnoreCase("yes")) {
                throw new BadRequestException(
                    i18n.tr("Error: Only pools with multi-entitlement product" +
                        " subscriptions can be added to the activation key with" +
                        " a quantity greater than one."));
            }
        }
        if (quantity != null && (!pool.isUnlimited()) && (quantity > pool.getQuantity())) {
            throw new BadRequestException(
                i18n.tr("The quantity must not be greater than the total " +
                    "allowed for the pool"));
        }
        if (isPoolHostRestricted(pool) &&
            !StringUtils.isBlank(getKeyHostRestriction(key)) &&
            !getPoolRequiredHost(pool).equals(getKeyHostRestriction(key))) {
            throw new BadRequestException(
                i18n.tr("Activation keys can only use host restricted pools from " +
                    "a single host."));
        }
        key.addPool(pool, quantity);
        activationKeyCurator.update(key);
        return pool;
    }

    /**
     * @return a Pool
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

    @GET
    @Path("{activation_key_id}/release")
    @Produces(MediaType.APPLICATION_JSON)
    public Release getReleaseVersion(
            @PathParam("activation_key_id")
                @Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        if (key.getReleaseVer() != null) {
            return key.getReleaseVer();
        }
        return new Release("");
    }

    @POST
    @Path("{activation_key_id}/release")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Release setReleaseVersion(@PathParam("activation_key_id")
            @Verify(ActivationKey.class) String activationKeyId,
            Release release) {
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        key.setReleaseVer(release);
        activationKeyCurator.merge(key);
        return release;
    }

    private Pool findPool(String poolId) {
        Pool pool = poolManager.find(poolId);

        if (pool == null) {
            throw new BadRequestException(i18n.tr(
                "Pool with id {0} could not be found.", poolId));
        }
        return pool;
    }

    private String getKeyHostRestriction(ActivationKey ak) {
        for (ActivationKeyPool akp : ak.getPools()) {
            if (isPoolHostRestricted(akp.getPool())) {
                return akp.getPool().getAttributeValue("requires_host");
            }
        }
        return null;
    }

    private boolean isPoolHostRestricted(Pool pool) {
        String host = getPoolRequiredHost(pool);
        return !StringUtils.isBlank(host);
    }

    private String getPoolRequiredHost(Pool pool) {
        return (pool.getAttributeValue("requires_host"));
    }
}
