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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EventCurator;
import org.fedoraproject.candlepin.model.ExporterMetadata;
import org.fedoraproject.candlepin.model.ExporterMetadataCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.OwnerInfo;
import org.fedoraproject.candlepin.model.OwnerInfoCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.model.SubscriptionToken;
import org.fedoraproject.candlepin.model.SubscriptionTokenCurator;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.sync.Importer;
import org.fedoraproject.candlepin.sync.ImporterException;
import org.fedoraproject.candlepin.sync.SyncDataFormatException;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jboss.resteasy.util.GenericType;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.pinsetter.tasks.MigrateOwnerJob;
import org.fedoraproject.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.quartz.JobDetail;

/**
 * Owner Resource
 */
@Path("/owners")
public class OwnerResource {
    private OwnerCurator ownerCurator;
    private OwnerInfoCurator ownerInfoCurator;
    private PoolCurator poolCurator;
    private SubscriptionCurator subscriptionCurator;
    private SubscriptionTokenCurator subscriptionTokenCurator;
    private UserServiceAdapter userService;
    private ConsumerCurator consumerCurator;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private static Logger log = Logger.getLogger(OwnerResource.class);
    private EventCurator eventCurator;
    private ProductCurator productCurator;
    private Importer importer;
    private ExporterMetadataCurator exportCurator;
    private PoolManager poolManager;
    private static final int FEED_LIMIT = 1000;
    

    @Inject
    public OwnerResource(OwnerCurator ownerCurator, PoolCurator poolCurator,
        ProductCurator productCurator,
        SubscriptionCurator subscriptionCurator,
        SubscriptionTokenCurator subscriptionTokenCurator,
        ConsumerCurator consumerCurator, I18n i18n,
        UserServiceAdapter userService, EventSink sink,
        EventFactory eventFactory, EventCurator eventCurator, Importer importer,
        PoolManager poolManager, ExporterMetadataCurator exportCurator,
        OwnerInfoCurator ownerInfoCurator) {

        this.ownerCurator = ownerCurator;
        this.ownerInfoCurator = ownerInfoCurator;
        this.productCurator = productCurator;
        this.poolCurator = poolCurator;
        this.subscriptionCurator = subscriptionCurator;
        this.subscriptionTokenCurator = subscriptionTokenCurator;
        this.consumerCurator = consumerCurator;
        this.userService = userService;
        this.i18n = i18n;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.eventCurator = eventCurator;
        this.importer = importer;
        this.exportCurator = exportCurator;
        this.poolManager = poolManager;
    }

    /**
     * Return list of Owners.
     * 
     * @return list of Owners
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "owners")      
    public List<Owner> list(@QueryParam("key") String keyFilter) {

        // For now, assuming key filter is just one key:
        if (keyFilter != null) {
            List<Owner> results = new LinkedList<Owner>();
            Owner o = ownerCurator.lookupByKey(keyFilter);
            if (o != null) {
                results.add(o);
            }
            return results;
        }

        return ownerCurator.listAll();
    }

    /**
     * Return the owner identified by the given ID.
     * 
     * @param ownerKey Owner ID.
     * @return the owner identified by the given id.
     */
    @GET
    @Path("/{owner_key}")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowRoles(roles = {Role.OWNER_ADMIN})    
    public Owner getOwner(@PathParam("owner_key") String ownerKey) {
        return findOwner(ownerKey);
    }

    /**
     * Return the owner's info identified by the given ID.
     * 
     * @param ownerKey Owner ID.
     * @return the info of the owner identified by the given id.
     */
    @GET
    @Path("/{owner_key}/info")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowRoles(roles = {Role.OWNER_ADMIN})    
    public OwnerInfo getOwnerInfo(@PathParam("owner_key") String ownerKey) {
        Owner owner = findOwner(ownerKey);
        return ownerInfoCurator.lookupByOwner(owner);
    }

    /**
     * Creates a new Owner
     * @return the new owner
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Owner createOwner(Owner owner) {
        Owner parent = owner.getParentOwner(); 
        if (parent != null &&
            ownerCurator.find(parent.getId()) == null) {
            throw new BadRequestException(
                i18n.tr("Cound not create the Owner: {0}. Parent {1} does not exist.",
                    owner, parent));
        }
        Owner toReturn = ownerCurator.create(owner);
     
        sink.emitOwnerCreated(owner);
        
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
    @Path("/{owner_key}")    
    @Produces(MediaType.APPLICATION_JSON)
    //FIXME No way this is as easy as this :)
    public void deleteOwner(@PathParam("owner_key") String ownerKey,
            @QueryParam("revoke") @DefaultValue("true") boolean revoke,
            @Context Principal principal) {
        Owner owner = findOwner(ownerKey);
        Event e = eventFactory.ownerDeleted(owner);

        cleanupAndDelete(owner, revoke);
        
        sink.sendEvent(e);
    }    

    private void cleanupAndDelete(Owner owner, boolean revokeCerts) {
        log.info("Cleaning up owner: " + owner);
        for (User u : userService.listByOwner(owner)) {
            userService.deleteUser(u);
        }
        for (Consumer c : consumerCurator.listByOwner(owner)) {
            log.info("Deleting consumer: " + c);

            if (revokeCerts) {
                poolManager.revokeAllEntitlements(c);
            }
            else {
                // otherwise just remove them without touching the CRL
                poolManager.removeAllEntitlements(c);
            }
            
            // need to check if this has been removed due to a 
            // parent being deleted
            // TODO:  There has to be a more efficient way to do this...
            c = consumerCurator.find(c.getId());
            if (c != null) {
                consumerCurator.delete(c);
            }
        }
        for (SubscriptionToken token : subscriptionTokenCurator.listByOwner(owner)) {
            log.info("Deleting subscription token: " + token);
            subscriptionTokenCurator.delete(token);
        }
        for (Subscription s : subscriptionCurator.listByOwner(owner)) {
            log.info("Deleting subscription: " + s);
            subscriptionCurator.delete(s);
        }
        for (Pool p : poolCurator.listByOwner(owner)) {
            log.info("Deleting pool: " + p);
            poolCurator.delete(p);
        }
        ExporterMetadata m =
            exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner);
        if (m != null) {
            log.info("Deleting export metadata: " + m);
            exportCurator.delete(m);
        }
        log.info("Deleting owner: " + owner);
        ownerCurator.delete(owner);
    }

    /**
     * Return the entitlements for the owner of the given id.
     * 
     * @param ownerKey
     *            id of the owner whose entitlements are sought.
     * @return the entitlements for the owner of the given id.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/entitlements")
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public List<Entitlement> ownerEntitlements(
        @PathParam("owner_key") String ownerKey) {
        Owner owner = findOwner(ownerKey);

        List<Entitlement> toReturn = new LinkedList<Entitlement>();
        for (Pool pool : owner.getPools()) {
            toReturn.addAll(poolCurator.entitlementsIn(pool));
        }

        return toReturn;
    }

    /**
     * Return the consumers for the owner of the given id.
     * @param ownerKey id of the owner whose consumers are sought.
     * @return the consumers for the owner of the given id.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/consumers")
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public List<Consumer> ownerConsumers(
        @PathParam("owner_key") String ownerKey) {

        Owner owner = findOwner(ownerKey);
        return new LinkedList<Consumer>(owner.getConsumers());
    }
    
    /**
     * Return the entitlement pools for the owner of the given id.
     * 
     * @param ownerKey
     *            id of the owner whose entitlement pools are sought.
     * @return the entitlement pools for the owner of the given id.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/pools")
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public List<Pool> ownerEntitlementPools(
        @PathParam("owner_key") String ownerKey) {
        Owner owner = findOwner(ownerKey);
    
        return poolCurator.listByOwner(owner);
    }
    
    // ----- User -----
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/users")
    public User createUser(@PathParam("owner_key") String ownerKey, User user) {
        Owner owner = findOwner(ownerKey);
        user.setOwner(owner);
        
        return userService.createUser(user);
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")
    public Subscription createSubscription(@PathParam("owner_key") String ownerKey, 
        Subscription subscription) {
        Owner o = findOwner(ownerKey);
        subscription.setOwner(o);
        subscription.setProduct(productCurator.find(subscription.getProduct().getId()));
        Set<Product> provided = new HashSet<Product>();
        for (Product incoming : subscription.getProvidedProducts()) {
            provided.add(productCurator.find(incoming.getId()));
        }
        subscription.setProvidedProducts(provided);
        Subscription s = subscriptionCurator.create(subscription);
        return s;
    }

    @GET
    @Produces("application/atom+xml")
    @Path("{owner_key}/atom")
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public Feed getOwnerAtomFeed(@PathParam("owner_key") String ownerKey) {
        Owner o = findOwner(ownerKey);
        Feed feed = this.eventCurator.toFeed(this.eventCurator.listMostRecent(FEED_LIMIT,
            o));
        feed.setTitle("Event feed for owner " + o.getDisplayName());
        return feed;
    }
    
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")    
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public List<Subscription> getSubscriptions(@PathParam("owner_key") String ownerKey) {
        return subscriptionCurator.listByOwner(findOwner(ownerKey));
    }
    
    /**
     *
     *
     * @param ownerKey
     * @return list of users under that owner name
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/users")
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public List<User> getUsers(@PathParam("owner_key") String ownerKey) {
        Owner o = findOwner(ownerKey);
        return userService.listByOwner(o);
    }
    
    private Owner findOwner(String key) {
        Owner owner = ownerCurator.lookupByKey(key);
        
        if (owner == null) {
            throw new NotFoundException(
                i18n.tr("owner with key: {0} was not found.", key));
        }
        
        return owner;
    }
   

    /**
     * expose updates for owners
     * @param key
     * @param owner
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}")
    @Transactional
    @AllowRoles(roles = { Role.OWNER_ADMIN })
    public void updateOwner(@PathParam("owner_key") String key,
        Owner owner) {
        Owner toUpdate = findOwner(key);
        log.debug("Updating");
        toUpdate.setDisplayName(owner.getDisplayName());
        toUpdate.setKey(owner.getKey());
        toUpdate.setParentOwner(owner.getParentOwner());
        ownerCurator.merge(toUpdate);    
    }


    /**
     * 'Tickle' an owner to have all of their entitlement pools synced with their
     * subscriptions.
     * 
     * This method (and the one below may not be entirely RESTful, as the updated data is
     * not supplied as an argument.
     * 
     * @param ownerKey unique id key of the owner whose pools should be updated
     * @return the status of the pending job
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")
    public JobDetail refreshPools(@PathParam("owner_key") String ownerKey,
        @QueryParam("auto_create_owner") @DefaultValue("false") Boolean autoCreateOwner) {

        Owner owner = ownerCurator.lookupByKey(ownerKey);
        if (owner == null) {
            if (autoCreateOwner) {
                owner = this.createOwner(new Owner(ownerKey, ownerKey));
            }
            else {
                throw new NotFoundException(i18n.tr(
                    "owner with key: {0} was not found.", ownerKey));
            }
        }

        return RefreshPoolsJob.forOwner(owner);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("migrate")
    public JobDetail migrateOwner(@QueryParam("id") String ownerKey,
        @QueryParam("uri") String url,
        @QueryParam("delete") @DefaultValue("true") boolean delete) {
        
        if (log.isDebugEnabled()) {
            log.debug("launch migrate owner - owner [" + ownerKey +
                "], uri [" + url + "]");
        }

        return MigrateOwnerJob.migrateOwner(ownerKey, url, delete);
    }

    @PUT
    @Path("/subscriptions")
    public void updateSubscription(Subscription subscription) {
        //TODO: Do we even need the owner id here?
        Subscription existingSubscription = this.subscriptionCurator
            .find(subscription.getId());
        if (existingSubscription == null) {
            throw new NotFoundException(i18n.tr(
                "subscription with id: {0} not found.", subscription.getId()));
        }
        this.subscriptionCurator.merge(subscription);
    }

    @POST
    @Path("{owner_key}/import")
    @AllowRoles(roles = Role.SUPER_ADMIN)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void importData(@PathParam("owner_key") String ownerKey, MultipartInput input) {
        Owner owner = findOwner(ownerKey);
        
        try {
            InputPart part = input.getParts().get(0);
            File archive = part.getBody(new GenericType<File>(){});
            log.info("Importing archive: " + archive.getAbsolutePath());
            importer.loadExport(owner, archive);
            
            sink.emitImportCreated(owner);
        }
        catch (IOException e) {
            throw new IseException(i18n.tr("Error reading export archive"), e);
        }
        catch (SyncDataFormatException e) {
            throw new BadRequestException(i18n.tr("Bad data in export archive"), e);
        }
        // These come back with internationalized messages, so we can transfer:
        catch (ImporterException e) {
            throw new IseException(e.getMessage(), e);
        }
    }
}
