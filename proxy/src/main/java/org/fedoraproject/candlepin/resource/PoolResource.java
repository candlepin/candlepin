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

import java.util.Date;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.DatatypeConverter;

import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import org.fedoraproject.candlepin.controller.PoolManager;

/**
 * API gateway for the EntitlementPool
 */

@Path("/pools")
public class PoolResource {

    private PoolCurator poolCurator;
    private PoolManager poolManager;
    private ConsumerCurator consumerCurator;
    private OwnerCurator ownerCurator;
    private I18n i18n;

    @Inject
    public PoolResource(
        PoolCurator poolCurator,
        ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        I18n i18n,
        EventSink eventSink, PoolManager poolManager) {
        this.poolCurator = poolCurator;
        this.consumerCurator = consumerCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.poolManager = poolManager;
    }

    private Date parseActiveOnString(String activeOn) {
        Date d;
        try {
            d = DatatypeConverter.parseDateTime(activeOn).getTime();
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid date, must use ISO 8601 format");
        }
        return d;
    }

    /**
     * Returns the list of available entitlement pools.
     * 
     * @param ownerId
     *            optional parameter to limit the search by owner
     * @param productId
     *            optional parameter to limit the search by product
     * @param consumerUuid
     *            optional parameter to limit the search by consumer,
     *            and only for applicable pools      
     * @param listAll
     *            use with consumerUuid to list all pools for the consumer's owner 
     * @return the list of available entitlement pools.
     *
     * @httpcode 200 if the request succeeded
     * @httpcode 400 if both consumer and owner are given, or if a product id is
     *               specified without a consumer or owner
     * @httpcode 404 if a specified consumer or owner is not found
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "pools")
    @AllowRoles(roles = {Role.OWNER_ADMIN, Role.CONSUMER})
    public List<Pool> list(@QueryParam("owner") String ownerId,
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("product") String productId,
        @QueryParam("listall") @DefaultValue("false") boolean listAll,
        @QueryParam("activeon") String activeOn) {
        
        // Make sure we were given sane query parameters:
        if (consumerUuid != null && ownerId != null) {
            throw new BadRequestException(
                i18n.tr("Cannot filter on both owner and consumer"));
        }
        if (consumerUuid == null && ownerId == null && productId != null) {
            throw new BadRequestException(
                i18n.tr("A consumer or owner is needed to filter on product"));
        }

        Date activeOnDate = new Date();
        if (activeOn != null) {
            activeOnDate = parseActiveOnString(activeOn);
        }
        
        Consumer c = null;
        Owner o = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("consumer: {0}", consumerUuid));
            }
            if (listAll) {
                o = c.getOwner();
                c = null;
            }
        }
        if (ownerId != null) {
            o = ownerCurator.find(ownerId);
            if (o == null) {
                throw new NotFoundException(i18n.tr("owner: {0}", ownerId));
            }
        }
        return poolCurator.listAvailableEntitlementPools(c, o, productId, true,
            activeOnDate);
    }

    /**
     * Creates a new pool - NOTE:  This method should probably be removed!
     *
     * The preferred method of creating pools is to first create a subscription,
     * and then refresh pools for the owner.
     *
     * @deprecated in favor of using the subscription resource
     *
     * @param pool the pool to create
     * @return the newly created pool
     *
     * @httpcode 200 if the pool was created successfully
     * @httpcode 404 if the posted pool's owner is not found
     * @httpcode 400 if there is an error while creating the pool
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    public Pool createPool(Pool pool) {
        // BOOO! We assume that pool.owner is partially constructed
        // (alternatively: we only care about the id field) - take it any way you want.
        // passing owner URI instead would be spiffy (not to say RESTful).
        Owner owner = ownerCurator.find(pool.getOwner().getId());
        if (owner == null) {
            throw new NotFoundException(
                i18n.tr("Owner with UUID '{0}' could not be found", 
                    pool.getOwner().getId()));
        }

        pool.setOwner(owner);
        
        Pool toReturn = poolManager.createPool(pool);
        if (toReturn != null) {
            // TODO:  This should probably return a 201 CREATED instead!

            return toReturn;
        }

        throw new BadRequestException(
            i18n.tr("Cound not create the Pool: {0}", pool));
    }

    /**
     * Return the Entitlement Pool for the given id
     * 
     * @param id
     *            the id of the pool
     * @return the pool identified by the id
     *
     * @httpcode 200 if the request succeeded
     * @httpcode 404 if the pool with the specified id is not found
     */
    @GET
    @Path("/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowRoles(roles = {Role.OWNER_ADMIN, Role.CONSUMER})
    public Pool getPool(@PathParam("pool_id") String id) {
        Pool toReturn = poolCurator.find(id);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException(
            i18n.tr("Entitlement Pool with ID '{0}' could not be found", id));
    }

    /**
     * Delete the pool with the given id
     *
     * @param id the id of the pool to delete
     *
     * @httpcode 200 if the pool was deleted successfully 
     * @httpcode 404 if the pool with the given id is not found
     * @httpdcode 403 if the pool is backed by a subscription
     */
    @DELETE
    @Path("/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deletePool(@PathParam("pool_id") String id) {
        Pool toReturn = poolCurator.find(id);

        if (toReturn == null) {
            throw new NotFoundException(
                i18n.tr("Entitlement Pool with ID '{0}' could not be found", id));
        }
        
        // Block attempts to delete pools backed by a subscription.
        if (toReturn.getSubscriptionId() != null) {
            throw new ForbiddenException(
                i18n.tr("Cannot directly delete a pool backed by a subscription"));
        }
        
        poolManager.deletePool(toReturn);

        // TODO:  This should be returning a 204 as there is no content to return
    }
}
