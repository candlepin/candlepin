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

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.common.paging.Paginate;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Cdn;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ResourceDateParser;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * API gateway for the EntitlementPool
 */

@Path("/pools")
@Api("pools")
public class PoolResource {

    private ConsumerCurator consumerCurator;
    private OwnerCurator ownerCurator;
    private I18n i18n;
    private PoolManager poolManager;
    private CalculatedAttributesUtil calculatedAttributesUtil;

    @Inject
    public PoolResource(ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        I18n i18n, PoolManager poolManager,
        CalculatedAttributesUtil calculatedAttributesUtil) {

        this.consumerCurator = consumerCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.poolManager = poolManager;
        this.calculatedAttributesUtil = calculatedAttributesUtil;
    }

    /**
     * @deprecated Use the method on /owners
     * @return List of pools
     */
    @ApiOperation(
        notes = "Retrieves a list of Pools @deprecated Use the method on /owners",
        value = "")
    @ApiResponses({
        @ApiResponse(code = 400,
        message = "if both consumer(unit) and owner are given, or if a" +
        " product id is specified without a consumer(unit) or owner"),
        @ApiResponse(code = 404, message = "if a specified consumer(unit) or owner is not found"),
        @ApiResponse(code = 403, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "pools")
    @Deprecated
    @SecurityHole
    @Paginate
    public List<Pool> list(@QueryParam("owner") String ownerId,
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("product") String productId,
        @ApiParam("Use with consumerUuid to list all pools available to the consumer. " +
        "This will include pools which would otherwise be omitted due to a rules" +
        " warning. (i.e. not recommended) Pools that trigger an error however will" +
        " still be omitted. (no entitlements available, consumer type mismatch, etc)")
        @QueryParam("listall") @DefaultValue("false") boolean listAll,
        @QueryParam("activeon") String activeOn,
        @Context Principal principal,
        @Context PageRequest pageRequest) {

        // Make sure we were given sane query parameters:
        if (consumerUuid != null && ownerId != null) {
            throw new BadRequestException(
                i18n.tr("Cannot filter on both owner and unit"));
        }
        if (consumerUuid == null && ownerId == null && productId != null) {
            throw new BadRequestException(
                i18n.tr("A unit or owner is needed to filter on product"));
        }

        Date activeOnDate = new Date();
        if (activeOn != null) {
            activeOnDate = ResourceDateParser.parseDateString(activeOn);
        }

        Consumer c = null;
        Owner o = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("Unit: {0} not found",
                    consumerUuid));
            }

            // Now that we have a consumer, check that this principal can access it:
            if (!principal.canAccess(c, SubResource.NONE, Access.READ_ONLY)) {
                throw new ForbiddenException(i18n.tr("User {0} cannot access unit {1}",
                    principal.getPrincipalName(), consumerUuid));
            }

            if (listAll) {
                o = c.getOwner();
            }
        }
        if (ownerId != null) {
            o = ownerCurator.secureFind(ownerId);
            if (o == null) {
                throw new NotFoundException(i18n.tr("owner: {0}", ownerId));
            }
            // Now that we have an owner, check that this principal can access it:
            if (!principal.canAccess(o, SubResource.POOLS, Access.READ_ONLY)) {
                throw new ForbiddenException(i18n.tr("User {0} cannot access owner {1}",
                    principal.getPrincipalName(), o.getKey()));
            }
        }

        // If we have no consumer, and no owner specified, kick 'em out unless they
        // have full system access (this is the same as requesting all pools in
        // the system).
        if (consumerUuid == null && ownerId == null && !principal.hasFullAccess()) {
            throw new ForbiddenException(i18n.tr("User {0} cannot access all pools.",
                    principal.getPrincipalName()));
        }

        Page<List<Pool>> page = poolManager.listAvailableEntitlementPools(c, null, o,
            productId, null, activeOnDate, true, listAll, new PoolFilterBuilder(), pageRequest);
        List<Pool> poolList = page.getPageData();

        calculatedAttributesUtil.setCalculatedAttributes(poolList, activeOnDate);
        calculatedAttributesUtil.setQuantityAttributes(poolList, c, activeOnDate);

        // Store the page for the LinkHeaderResponseFilter
        ResteasyProviderFactory.pushContext(Page.class, page);
        return poolList;
    }

    @ApiOperation(notes = "Retrieves a single Pool", value = "getPool")
    @ApiResponses({ @ApiResponse(code = 404, message = "if the pool with the specified id is not found"),
        @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pool getPool(@PathParam("pool_id") @Verify(Pool.class) String id,
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("activeon") String activeOn,
        @Context Principal principal) {
        Pool toReturn = poolManager.find(id);

        Consumer c = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("consumer: {0} not found",
                    consumerUuid));
            }

            if (!principal.canAccess(c, SubResource.NONE, Access.READ_ONLY)) {
                throw new ForbiddenException(i18n.tr("User {0} cannot access consumer {1}",
                    principal.getPrincipalName(), c.getUuid()));
            }
        }

        if (toReturn != null) {
            Date activeOnDate = new Date();
            if (activeOn != null) {
                activeOnDate = ResourceDateParser.parseDateString(activeOn);
            }
            toReturn.setCalculatedAttributes(
                calculatedAttributesUtil.buildCalculatedAttributes(toReturn, activeOnDate)
            );
            calculatedAttributesUtil.setQuantityAttributes(toReturn, c, activeOnDate);
            return toReturn;
        }

        throw new NotFoundException(i18n.tr(
            "Subscription Pool with ID ''{0}'' could not be found.", id));
    }

    @ApiOperation(notes = "Remove a Pool", value = "deletePool")
    @ApiResponses({ @ApiResponse(code = 404, message = "if the pool with the specified id is not found") })
    @DELETE
    @Path("/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deletePool(@PathParam("pool_id") String id) {
        Pool pool = poolManager.find(id);
        if (pool == null) {
            throw new NotFoundException(i18n.tr(
                "Entitlement Pool with ID ''{0}'' could not be found.", id));
        }

        poolManager.deletePool(pool);
    }

    @ApiOperation(notes = "Retrieve a CDN for a Pool", value = "getPoolCdn")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Path("{pool_id}/cdn")
    @Produces(MediaType.APPLICATION_JSON)
    public Cdn getPoolCdn(
        @PathParam("pool_id") @Verify(Pool.class) String id) {

        Pool pool = poolManager.find(id);

        if (pool == null) {
            throw new NotFoundException(i18n.tr(
                "Subscription Pool with ID ''{0}'' could not be found.", id));
        }

        return pool.getCdn();
    }

    @ApiOperation(notes = "Retrieve a list of Entitlements for a Pool", value = "getPoolEntitlements")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Path("{pool_id}/entitlements")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Entitlement> getPoolEntitlements(@PathParam("pool_id")
        @Verify(value = Pool.class, subResource = SubResource.ENTITLEMENTS) String id,
        @Context Principal principal) {

        Pool pool = poolManager.find(id);

        if (pool == null) {
            throw new NotFoundException(i18n.tr(
                "Subscription Pool with ID ''{0}'' could not be found.", id));
        }

        List<Entitlement> entitlements = new ArrayList<Entitlement>();
        entitlements.addAll(pool.getEntitlements());
        return entitlements;
    }

}
