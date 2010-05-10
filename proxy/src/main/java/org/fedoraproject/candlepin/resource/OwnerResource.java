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

import java.util.LinkedList;
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
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * Owner Resource
 */
@Path("/owners")
public class OwnerResource {
    //private static Logger log = Logger.getLogger(OwnerResource.class);
    private OwnerCurator ownerCurator;
    private PoolCurator poolCurator;
    private SubscriptionCurator subscriptionCurator;
    private UserServiceAdapter userService;
    private ConsumerCurator consumerCurator;
    private I18n i18n;
    private Entitler entitler;
    private static Logger log = Logger.getLogger(OwnerResource.class);

    @Inject
    public OwnerResource(OwnerCurator ownerCurator, PoolCurator poolCurator, 
        SubscriptionCurator subscriptionCurator, ConsumerCurator consumerCurator, 
        I18n i18n, UserServiceAdapter userService, Entitler entitler) {
        this.ownerCurator = ownerCurator;
        this.poolCurator = poolCurator;
        this.subscriptionCurator = subscriptionCurator;
        this.consumerCurator = consumerCurator;
        this.userService = userService;
        this.i18n = i18n;
        this.entitler = entitler;
    }

    /**
     * Return list of Owners.
     * 
     * @return list of Owners
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Wrapped(element = "owners")    
    public List<Owner> list() {
        return ownerCurator.listAll();
    }

    /**
     * Return the owner identified by the given ID.
     * 
     * @param ownerId Owner ID.
     * @return the owner identified by the given id.
     */
    @GET
    @Path("/{owner_id}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Owner getOwner(@PathParam("owner_id") Long ownerId) {
        return findOwner(ownerId);
    }

    /**
     * Creates a new Owner
     * @return the new owner
     */
    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Owner createOwner(Owner owner) {
        Owner toReturn = ownerCurator.create(owner);

        if (toReturn != null) {
            return toReturn;
        }

        throw new BadRequestException(
            i18n.tr("Cound not create the Owner: {0}", owner));
    }
    
    /**
     * Deletes an owner
     */
    @DELETE
    @Path("/{owner_id}")    
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    //FIXME No way this is as easy as this :)
    public void deleteOwner(@PathParam("owner_id") Long ownerId) {
        Owner owner = findOwner(ownerId);
        
        cleanupAndDelete(owner);
    }    

    private void cleanupAndDelete(Owner owner) {
        log.info("Cleaning up owner: " + owner);
        for (User u : userService.listByOwner(owner)) {
            userService.deleteUser(u);
        }
        for (Consumer c : consumerCurator.listByOwner(owner)) {
            log.info("Deleting consumer: " + c);
            entitler.revokeAllEntitlements(c);
            consumerCurator.delete(c);
        }
        for (Pool p : poolCurator.listByOwner(owner)) {
            log.info("Deleting subscription: " + p.getSubscriptionId());
            Subscription s = subscriptionCurator.find(p.getSubscriptionId());
            if (s != null) {
                subscriptionCurator.delete(s);
            }
            
            log.info("Deleting pool: " + p);
            poolCurator.delete(p);
        }
        log.info("Deleting owner: " + owner);
        ownerCurator.delete(owner);
    }

    /**
     * Return the entitlements for the owner of the given id.
     * 
     * @param ownerId
     *            id of the owner whose entitlements are sought.
     * @return the entitlements for the owner of the given id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{owner_id}/entitlements")
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public List<Entitlement> ownerEntitlements(
        @PathParam("owner_id") Long ownerId) {
        Owner owner = findOwner(ownerId);

        List<Entitlement> toReturn = new LinkedList<Entitlement>();
        for (Pool pool : owner.getEntitlementPools()) {
            toReturn.addAll(poolCurator.entitlementsIn(pool));
        }

        return toReturn;
    }
    
    /**
     * Return the entitlement pools for the owner of the given id.
     * 
     * @param ownerId
     *            id of the owner whose entitlement pools are sought.
     * @return the entitlement pools for the owner of the given id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{owner_id}/pools")
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public List<Pool> ownerEntitlementPools(
        @PathParam("owner_id") Long ownerId) {
        Owner owner = findOwner(ownerId);
    
        return poolCurator.listByOwner(owner);
    }
    
    // ----- User -----
    @POST
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{owner_id}/users")
    public User createUser(@PathParam("owner_id") Long ownerId, User user) {
        Owner owner = findOwner(ownerId);
        user.setOwner(owner);
        
        return userService.createUser(user);
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{owner_id}/users/{user_id}")
    public User getUser(@PathParam("owner_id") Long ownerId, 
        @PathParam("user_id") Long userId) {
        
        // TODO: Add another method to the user service API?
        
        return null;
    }
    
    @POST
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{owner_id}/subscriptions")
    public Subscription createSubscription(@PathParam("owner_id") Long ownerId, 
        Subscription subscription) {
        
        subscription.setOwner(findOwner(ownerId));
        Subscription newSubscription = subscriptionCurator.create(subscription);

        return newSubscription;
    }
    
    @GET
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{owner_id}/subscriptions")    
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public List<Subscription> getSubscriptions(@PathParam("owner_id") Long ownerId) {
        List<Subscription> subList = new LinkedList<Subscription>();
        subList = subscriptionCurator.listByOwner(findOwner(ownerId));
        return subList;
    }
    
    private Owner findOwner(Long ownerId) {
        Owner owner = ownerCurator.find(ownerId);
        
        if (owner == null) {
            throw new NotFoundException(
                i18n.tr("owner with id: {0} was not found.", ownerId));
        }
        
        return owner;
    }
    
    /**
     * 'Tickle' an owner to have all of their entitlement pools synced with their
     * subscriptions.
     * 
     * This method (and the one below may not be entirely RESTful, as the updated data is
     * not supplied as an argument.
     * 
     * @param ownerKey unique id key of the owner whose pools should be updated
     */
    @PUT
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{owner_key}/subscriptions")
    public void refreshEntitlementPools(@PathParam("owner_key") String ownerKey) {
        Owner owner = ownerCurator.lookupByKey(ownerKey);
        if (owner == null) {
            throw new NotFoundException(
                i18n.tr("owner with key: {0} was not found.", ownerKey));
        }
        
        poolCurator.refreshPools(owner);
    }
}
