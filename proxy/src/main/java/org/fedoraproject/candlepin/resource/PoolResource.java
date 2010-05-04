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

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * API gateway for the EntitlementPool
 */

@Path("/pools")
public class PoolResource {
    //private static Logger log = Logger.getLogger(PoolResource.class);

    private PoolCurator poolCurator;
    private ConsumerCurator consumerCurator;
    private OwnerCurator ownerCurator;
    private ProductServiceAdapter productServiceAdapter;
    private I18n i18n;

    @Inject
    public PoolResource(
        PoolCurator poolCurator,
        ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        ProductServiceAdapter productServiceAdapter,
        I18n i18n) {
        this.poolCurator = poolCurator;
        this.consumerCurator = consumerCurator;
        this.ownerCurator = ownerCurator;
        this.productServiceAdapter = productServiceAdapter;
        this.i18n = i18n;
    }

    /**
     * Returns the list of available entitlement pools.
     * 
     * @param ownerId
     *            optional parameter to limit the search by owner
     * @param productId
     *            optional parameter to limit the search by product      
     * @return the list of available entitlement pools.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Wrapped(element = "pools")
    @AllowRoles(roles = {Role.OWNER_ADMIN, Role.CONSUMER})
    public List<Pool> list(@QueryParam("owner") Long ownerId,
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("product") String productId) {
        
        // Make sure we were given sane query parameters:
        if (consumerUuid != null && ownerId != null) {
            throw new BadRequestException(
                i18n.tr("Cannot filter on both owner and consumer"));
        }
        if (consumerUuid == null && ownerId == null && productId != null) {
            throw new BadRequestException(
                i18n.tr("A consumer or owner is needed to filter on product"));
        }

        if ((ownerId == null) && (productId == null) && (consumerUuid == null)) {
            return poolCurator.findAll();
        }
        else {
            Product p = null;
            if (productId != null) {
                p = productServiceAdapter.getProductById(productId);
                if (p == null) {
                    throw new NotFoundException(i18n.tr("product: {0}", productId));
                }
            }
            Consumer c = null;
            if (consumerUuid != null) {
                c = consumerCurator.lookupByUuid(consumerUuid);
                if (c == null) {
                    throw new NotFoundException(i18n.tr("consumer: {0}", consumerUuid));
                }                
            }        
            Owner o = null;
            if (ownerId != null) {
                o = ownerCurator.find(ownerId);
                if (o == null) {
                    throw new NotFoundException(i18n.tr("owner: {0}", ownerId));
                }                
            }                   
            return poolCurator.listAvailableEntitlementPools(c, o, p, true);
        }
    }
    
    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
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
        Pool toReturn = poolCurator.create(pool);
        
        if (toReturn != null) {
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
     */
    @GET
    @Path("/{pool_id}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Pool getProduct(@PathParam("pool_id") Long id) {
        Pool toReturn = poolCurator.find(id);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException(
            i18n.tr("Entitlement Pool with ID '{0}' could not be found", id));
    }

}
