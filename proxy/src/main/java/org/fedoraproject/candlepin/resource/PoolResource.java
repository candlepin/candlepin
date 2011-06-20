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
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.interceptor.SecurityHole;
import org.fedoraproject.candlepin.auth.interceptor.Verify;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Statistic;
import org.fedoraproject.candlepin.model.StatisticCurator;
import org.fedoraproject.candlepin.resource.util.ResourceDateParser;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * API gateway for the EntitlementPool
 */

@Path("/pools")
public class PoolResource {

    private PoolCurator poolCurator;
    private ConsumerCurator consumerCurator;
    private OwnerCurator ownerCurator;
    private StatisticCurator statisticCurator;
    private I18n i18n;

    @Inject
    public PoolResource(PoolCurator poolCurator,

        ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        StatisticCurator statisticCurator, I18n i18n,
        EventSink eventSink) {

        this.poolCurator = poolCurator;
        this.consumerCurator = consumerCurator;
        this.ownerCurator = ownerCurator;
        this.statisticCurator= statisticCurator;
        this.i18n = i18n;
    }

    /**
     * Returns the list of available entitlement pools.
     * 
     * @deprecated Use the method on /owners
     * @param ownerId optional parameter to limit the search by owner
     * @param productId optional parameter to limit the search by product
     * @param consumerUuid optional parameter to limit the search by consumer,
     *        and only for applicable pools
     * @param listAll Use with consumerUuid to list all pools available to the
     *        consumer. This will include pools which would otherwise be omitted
     *        due to a rules warning. (i.e. not recommended) Pools that trigger
     *        an error however will still be omitted. (no entitlements
     *        available, consumer type mismatch, etc)
     * @return the list of available entitlement pools.
     * @httpcode 200 if the request succeeded
     * @httpcode 400 if both consumer and owner are given, or if a product id is
     *           specified without a consumer or owner
     * @httpcode 404 if a specified consumer or owner is not found
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "pools")
    @Deprecated
    @SecurityHole
    public List<Pool> list(@QueryParam("owner") String ownerId,
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("product") String productId,
        @QueryParam("listall") @DefaultValue("false") boolean listAll,
        @QueryParam("activeon") String activeOn, @Context Principal principal) {

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
            activeOnDate = ResourceDateParser.parseDateString(activeOn);
        }

        Consumer c = null;
        Owner o = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("consumer: {0} not found",
                    consumerUuid));
            }
            
            // Now that we have a consumer, check that this principal can access it:
            if (!principal.canAccess(c, Access.READ_ONLY)) {
                throw new ForbiddenException(i18n.tr("User {0} cannot access consumer {1}", 
                    principal.getPrincipalName(), consumerUuid));
            }

            if (listAll) {
                o = c.getOwner();
            }
        }
        if (ownerId != null) {
            o = ownerCurator.find(ownerId);
            if (o == null) {
                throw new NotFoundException(i18n.tr("owner: {0}", ownerId));
            }
            // Now that we have an owner, check that this principal can access it:
            if (!principal.canAccess(o, Access.READ_POOLS)) {
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
        
        return poolCurator.listAvailableEntitlementPools(c, o, productId,
            activeOnDate, true, listAll);
    }

    /**
     * Return the Entitlement Pool for the given id
     * 
     * @param id the id of the pool
     * @return the pool identified by the id
     * @httpcode 200 if the request succeeded
     * @httpcode 404 if the pool with the specified id is not found
     */
    @GET
    @Path("/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pool getPool(@PathParam("pool_id") @Verify(Pool.class) String id) {
        Pool toReturn = poolCurator.find(id);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException(i18n.tr(
            "Entitlement Pool with ID '{0}' could not be found", id));
    }

    @GET
    @Path("{pool_id}/statistics")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Statistic> getPoolStats(@PathParam("pool_id") String id,
                            @QueryParam("from") String from,
                            @QueryParam("to") String to,
                            @QueryParam("days") String days) {

        return statisticCurator.getStatisticsByPool(id, null,
                                ResourceDateParser.getFromDate(from, to, days),
                                ResourceDateParser.parseDateString(to));
    }

    @GET
    @Path("{pool_id}/statistics/{vtype}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Statistic> getPoolStats(@PathParam("pool_id") String id,
                            @PathParam("vtype") String valueType,
                            @QueryParam("from") String from,
                            @QueryParam("to") String to,
                            @QueryParam("days") String days) {

        return statisticCurator.getStatisticsByPool(id, valueType,
                                ResourceDateParser.getFromDate(from, to, days),
                                ResourceDateParser.parseDateString(to));
    }

}
