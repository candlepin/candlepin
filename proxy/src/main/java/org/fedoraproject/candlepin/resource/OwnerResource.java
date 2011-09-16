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
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

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
import org.fedoraproject.candlepin.audit.EventAdapter;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.interceptor.Verify;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.CandlepinException;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.ActivationKey;
import org.fedoraproject.candlepin.model.ActivationKeyCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.EventCurator;
import org.fedoraproject.candlepin.model.ExporterMetadata;
import org.fedoraproject.candlepin.model.ExporterMetadataCurator;
import org.fedoraproject.candlepin.model.ImportRecord;
import org.fedoraproject.candlepin.model.ImportRecordCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.OwnerInfo;
import org.fedoraproject.candlepin.model.OwnerInfoCurator;
import org.fedoraproject.candlepin.model.OwnerPermission;
import org.fedoraproject.candlepin.model.OwnerPermissionCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.model.Statistic;
import org.fedoraproject.candlepin.model.StatisticCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.resource.util.ResourceDateParser;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.UniqueIdGenerator;
import org.fedoraproject.candlepin.sync.Importer;
import org.fedoraproject.candlepin.sync.ImporterException;
import org.fedoraproject.candlepin.sync.SyncDataFormatException;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jboss.resteasy.util.GenericType;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;


/**
 * Owner Resource
 */
@Path("/owners")
public class OwnerResource {
    /**
     * 
     */
    public static final String UEBER_CERT_CONSUMER = "ueber_cert_consumer";
    private OwnerCurator ownerCurator;
    private OwnerInfoCurator ownerInfoCurator;
    private PoolCurator poolCurator;
    private SubscriptionCurator subscriptionCurator;
    private ActivationKeyCurator activationKeyCurator;
    private StatisticCurator statisticCurator;
    private SubscriptionServiceAdapter subService;
    private ConsumerCurator consumerCurator;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private EventAdapter eventAdapter;
    private static Logger log = Logger.getLogger(OwnerResource.class);
    private EventCurator eventCurator;
    private Importer importer;
    private ExporterMetadataCurator exportCurator;
    private ImportRecordCurator importRecordCurator;
    private OwnerPermissionCurator permissionCurator;
    private PoolManager poolManager;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductServiceAdapter prodAdapter;
    private UniqueIdGenerator idGenerator;
    private ContentCurator contentCurator;
    private EntitlementCertificateCurator entitlementCertCurator;
    private EntitlementCurator entitlementCurator;
    private static final int FEED_LIMIT = 1000;

    @Inject
    public OwnerResource(OwnerCurator ownerCurator, PoolCurator poolCurator,
        SubscriptionCurator subscriptionCurator,
        ActivationKeyCurator activationKeyCurator,
        ConsumerCurator consumerCurator,
        StatisticCurator statisticCurator,
        I18n i18n, EventSink sink,
        EventFactory eventFactory, EventCurator eventCurator,
        EventAdapter eventAdapter, Importer importer,
        PoolManager poolManager, ExporterMetadataCurator exportCurator,
        OwnerInfoCurator ownerInfoCurator,
        ImportRecordCurator importRecordCurator,
        SubscriptionServiceAdapter subService,
        OwnerPermissionCurator permCurator,
        ConsumerTypeCurator consumerTypeCurator,
        ProductServiceAdapter prodAdapter,
        ContentCurator contentCurator,
        EntitlementCertificateCurator entitlementCertCurator,
        EntitlementCurator entitlementCurator, UniqueIdGenerator idGenerator) {

        this.ownerCurator = ownerCurator;
        this.ownerInfoCurator = ownerInfoCurator;
        this.poolCurator = poolCurator;
        this.subscriptionCurator = subscriptionCurator;
        this.activationKeyCurator = activationKeyCurator;
        this.consumerCurator = consumerCurator;
        this.statisticCurator = statisticCurator;
        this.i18n = i18n;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.eventCurator = eventCurator;
        this.importer = importer;
        this.exportCurator = exportCurator;
        this.importRecordCurator = importRecordCurator;
        this.poolManager = poolManager;
        this.eventAdapter = eventAdapter;
        this.subService = subService;
        this.permissionCurator = permCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.prodAdapter = prodAdapter;
        this.contentCurator = contentCurator;
        this.idGenerator = idGenerator;
        this.entitlementCertCurator = entitlementCertCurator;
        this.entitlementCurator = entitlementCurator;
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
    public Owner getOwner(@PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
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
    public OwnerInfo getOwnerInfo(@PathParam("owner_key")
        @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwner(ownerKey);
        return ownerInfoCurator.lookupByOwner(owner);
    }

    /**
     * Creates a new Owner
     *
     * @return the new owner
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Owner createOwner(Owner owner) {
        Owner parent = owner.getParentOwner();
        if (parent != null && ownerCurator.find(parent.getId()) == null) {
            throw new BadRequestException(i18n.tr(
                "Cound not create the Owner: {0}. Parent {1} does not exist.",
                owner, parent));
        }
        Owner toReturn = ownerCurator.create(owner);

        sink.emitOwnerCreated(owner);

        if (toReturn != null) {
            return toReturn;
        }

        throw new BadRequestException(i18n.tr(
            "Cound not create the Owner: {0}", owner));
    }

    /**
     * Deletes an owner
     */
    @DELETE
    @Path("/{owner_key}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteOwner(@PathParam("owner_key") String ownerKey,
        @QueryParam("revoke") @DefaultValue("true") boolean revoke) {
        Owner owner = findOwner(ownerKey);
        Event e = eventFactory.ownerDeleted(owner);

        cleanupAndDelete(owner, revoke);

        sink.sendEvent(e);
    }

    private void cleanupAndDelete(Owner owner, boolean revokeCerts) {
        log.info("Cleaning up owner: " + owner);
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
            // TODO: There has to be a more efficient way to do this...
            c = consumerCurator.find(c.getId());
            if (c != null) {
                consumerCurator.delete(c);
            }
        }
        for (ActivationKey key : activationKeyCurator
            .listByOwner(owner)) {
            log.info("Deleting activation key: " + key);
            activationKeyCurator.delete(key);
        }
        for (Subscription s : subscriptionCurator.listByOwner(owner)) {
            log.info("Deleting subscription: " + s);
            subscriptionCurator.delete(s);
        }
        for (Pool p : poolCurator.listByOwner(owner)) {
            log.info("Deleting pool: " + p);
            poolCurator.delete(p);
        }
        ExporterMetadata m = exportCurator.lookupByTypeAndOwner(
            ExporterMetadata.TYPE_PER_USER, owner);
        if (m != null) {
            log.info("Deleting export metadata: " + m);
            exportCurator.delete(m);
        }
        for (ImportRecord record : importRecordCurator.findRecords(owner)) {
            log.info("Deleting import record:  " + record);
            importRecordCurator.delete(record);
        }

        for (OwnerPermission perm : permissionCurator.findByOwner(owner)) {
            log.info("Deleting permission: " + perm.getAccess());
            perm.getRole().getPermissions().remove(perm);
            permissionCurator.delete(perm);
        }

        log.info("Deleting owner: " + owner);
        ownerCurator.delete(owner);
    }

    /**
     * Return the entitlements for the owner of the given id.
     *
     * @param ownerKey id of the owner whose entitlements are sought.
     * @return the entitlements for the owner of the given id.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/entitlements")
    public List<Entitlement> ownerEntitlements(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwner(ownerKey);

        List<Entitlement> toReturn = new LinkedList<Entitlement>();
        for (Pool pool : owner.getPools()) {
            toReturn.addAll(poolCurator.entitlementsIn(pool));
        }

        return toReturn;
    }

    /**
     * Return the activation keys for the owner of the given id.
     *
     * @param ownerKey id of the owner whose keys are sought.
     * @return the activation keys for the owner of the given id.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/activation_keys")
    public List<ActivationKey> ownerActivationKeys(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwner(ownerKey);

        return this.activationKeyCurator.listByOwner(owner);
    }

    /**
     * Allow the creation of an activation key from the owner resource
     *
     * @param ownerKey id of the owner whose keys are sought.
     * @return the activation keys for the owner of the given id.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/activation_keys")
    public ActivationKey createActivationKey(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        ActivationKey activationKey) {

        Owner owner = findOwner(ownerKey);
        activationKey.setOwner(owner);
        
        if (activationKey.getName() == null || activationKey.getName().trim().equals("")) {
            throw new BadRequestException(
                i18n.tr("Must provide a name for activation key."));
        }

        String testName = activationKey.getName().replace("-", "0")
                          .replace("_", "0");
        if (!testName.matches("[a-zA-Z0-9]*")) {
            throw new BadRequestException(
                i18n.tr("Activation key names must be alphanumeric or the " +
                    "characters '-' or '_'. " +
                    "[" + activationKey.getName() + "]"));
        }

        if (activationKeyCurator.lookupForOwner(activationKey.getName(), owner) != null) {
            throw new BadRequestException(
                i18n.tr("Activation key name [" + activationKey.getName() +
                    "] is already in use for owner [" + ownerKey + "]"));
        }


        ActivationKey newKey = activationKeyCurator.create(activationKey);
        sink.emitActivationKeyCreated(newKey);

        return newKey;
    }

    /**
     * Return the consumers for the owner of the given id.
     *
     * @param ownerKey id of the owner whose consumers are sought.
     * @return the consumers for the owner of the given id.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/consumers")
    public List<Consumer> ownerConsumers(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("username") String userName,
        @QueryParam("type") String typeLabel) {

        Owner owner = findOwner(ownerKey);

        ConsumerType type = null;

        if (typeLabel != null) {
            type = lookupConsumerType(typeLabel);
        }

        // We don't look up the user and warn if it doesn't exist here to not
        // give away usernames
        return consumerCurator.listByUsernameAndType(userName, type, owner);
    }

    private ConsumerType lookupConsumerType(String label) {
        ConsumerType type = consumerTypeCurator.lookupByLabel(label);

        if (type == null) {
            throw new BadRequestException(i18n.tr("No such consumer type: {0}",
                label));
        }
        return type;
    }


    /**
     * Return the entitlement pools for the owner of the given id.
     *
     * @param ownerKey id of the owner whose entitlement pools are sought.
     * @return the entitlement pools for the owner of the given id.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/pools")
    public List<Pool> getPools(
        @PathParam("owner_key")
            @Verify(value = Owner.class, require = Access.READ_POOLS) String ownerKey,
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("product") String productId,
        @QueryParam("listall") @DefaultValue("false") boolean listAll,
        @QueryParam("activeon") String activeOn) {

        Owner owner = findOwner(ownerKey);

        Date activeOnDate = new Date();
        if (activeOn != null) {
            activeOnDate = ResourceDateParser.parseDateString(activeOn);
        }

        Consumer c = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("consumer: {0} not found",
                    consumerUuid));
            }
            if (!c.getOwner().getId().equals(owner.getId())) {
                throw new BadRequestException(
                    "Consumer specified does not belong to owner on path");
            }
        }
        return poolCurator.listAvailableEntitlementPools(c, owner, productId,
            activeOnDate, true, listAll);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")
    public Subscription createSubscription(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        Subscription subscription) {
        Owner o = findOwner(ownerKey);
        subscription.setOwner(o);
        return subService.createSubscription(subscription);
    }

    @GET
    @Produces("application/atom+xml")
    @Path("{owner_key}/atom")
    public Feed getOwnerAtomFeed(@PathParam("owner_key")
            @Verify(Owner.class) String ownerKey) {
        Owner o = findOwner(ownerKey);
        String path = String.format("/owners/%s/atom", ownerKey);
        Feed feed = this.eventAdapter.toFeed(
            this.eventCurator.listMostRecent(FEED_LIMIT, o), path);
        feed.setTitle("Event feed for owner " + o.getDisplayName());
        return feed;
    }

    @GET
    @Produces("application/atom+xml")
    @Path("{owner_key}/consumers/{consumer_uuid}/atom")
    public Feed getConsumerAtomFeed(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @PathParam("consumer_uuid") String consumerUuid) {
        String path = String.format("/consumers/%s/atom", consumerUuid);
        Consumer consumer = findConsumer(consumerUuid);
        Feed feed = this.eventAdapter.toFeed(
            this.eventCurator.listMostRecent(FEED_LIMIT, consumer), path);
        feed.setTitle("Event feed for consumer " + consumer.getUuid());
        return feed;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/events")
    public List<Event> getEvents(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner o = findOwner(ownerKey);
        List<Event> events = this.eventCurator.listMostRecent(FEED_LIMIT, o);
        if (events != null) {
            eventAdapter.addMessageText(events);
        }
        return events;
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")
    public List<Subscription> getSubscriptions(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner o = findOwner(ownerKey);
        return subService.getSubscriptions(o);
    }

    private Owner findOwner(String key) {
        Owner owner = ownerCurator.lookupByKey(key);

        if (owner == null) {
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", key));
        }

        return owner;
    }

    private Consumer findConsumer(String consumerUuid) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);

        if (consumer == null) {
            throw new NotFoundException(i18n.tr("No such consumer: {0}",
                consumerUuid));
        }
        return consumer;
    }

    /**
     * expose updates for owners
     *
     * @param key
     * @param owner
     * @return the update {@link Owner}
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}")
    @Transactional
    public Owner updateOwner(@PathParam("owner_key") @Verify(Owner.class) String key,
        Owner owner) {
        Owner toUpdate = findOwner(key);
        log.debug("Updating");
        toUpdate.setDisplayName(owner.getDisplayName());
        toUpdate.setKey(owner.getKey());
        toUpdate.setParentOwner(owner.getParentOwner());
        ownerCurator.merge(toUpdate);
        return toUpdate;
    }

    /**
     * 'Tickle' an owner to have all of their entitlement pools synced with
     * their subscriptions. This method (and the one below may not be entirely
     * RESTful, as the updated data is not supplied as an argument.
     *
     * @param ownerKey unique id key of the owner whose pools should be updated
     * @return the status of the pending job
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")
    public JobDetail refreshPools(
        // TODO: Can we verify with autocreate?
        @PathParam("owner_key") String ownerKey,
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
    @Path("/subscriptions")
    public void updateSubscription(Subscription subscription) {
        // TODO: Do we even need the owner id here?
        Subscription existingSubscription = this.subscriptionCurator
            .find(subscription.getId());
        if (existingSubscription == null) {
            throw new NotFoundException(i18n.tr(
                "subscription with id: {0} not found.", subscription.getId()));
        }
        this.subscriptionCurator.merge(subscription);
    }

    @POST
    @Path("{owner_key}/imports")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void importData(@PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        MultipartInput input) {
        Owner owner = findOwner(ownerKey);

        try {
            InputPart part = input.getParts().get(0);
            File archive = part.getBody(new GenericType<File>() {
            });
            log.info("Importing archive: " + archive.getAbsolutePath());
            importer.loadExport(owner, archive);

            sink.emitImportCreated(owner);
            recordImportSuccess(owner);
        }
        catch (IOException e) {
            recordImportFailure(owner, e);
            throw new IseException(i18n.tr("Error reading export archive"), e);
        }
        catch (SyncDataFormatException e) {
            recordImportFailure(owner, e);
            throw new BadRequestException(
                i18n.tr("Bad data in export archive"), e);
        }
        // These come back with internationalized messages, so we can transfer:
        catch (ImporterException e) {
            recordImportFailure(owner, e);
            throw new IseException(e.getMessage(), e);
        }
        // Grab candlepin exceptions to record the error and then rethrow
        // to pass on the http return code
        catch (CandlepinException e) {
            recordImportFailure(owner, e);
            throw e;
        }
    }

    @GET
    @Path("{owner_key}/imports")
    public List<ImportRecord> getImports(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwner(ownerKey);

        return this.importRecordCurator.findRecords(owner);
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/statistics")
    public List<Statistic> getStatistics(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("days") String days) {
        Owner o = findOwner(ownerKey);

        if (o == null) {
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", ownerKey));
        }


        return statisticCurator.getStatisticsByOwner(o, "", "", "",
                                ResourceDateParser.getFromDate(from, to, days),
                                ResourceDateParser.parseDateString(to));
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/statistics/{type}")
    public List<Statistic> getStatistics(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @PathParam("type") String qType,
        @QueryParam("reference") String reference,
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("days") String days) {
        Owner o = findOwner(ownerKey);

        if (o == null) {
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", ownerKey));
        }

        return statisticCurator.getStatisticsByOwner(o, qType, reference, "",
                                ResourceDateParser.getFromDate(from, to, days),
                                ResourceDateParser.parseDateString(to));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/statistics/{qtype}/{vtype}")
    public List<Statistic> getStatistics(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @PathParam("qtype") String qType,
        @PathParam("vtype") String vType,
        @QueryParam("reference") String reference,
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("days") String days) {
        Owner o = findOwner(ownerKey);

        if (o == null) {
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", ownerKey));
        }


        return statisticCurator.getStatisticsByOwner(o, qType, reference, vType,
                                ResourceDateParser.getFromDate(from, to, days),
                                ResourceDateParser.parseDateString(to));
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/uebercert")
    public EntitlementCertificate createUeberCertificate(@Context Principal principal, 
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey) {                
        
        Owner o = findOwner(ownerKey);
        
        if (o == null) {
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", ownerKey));
        }
        
        Consumer ueberConsumer = consumerCurator.findByName(UEBER_CERT_CONSUMER);
        // ueber cert has already been generated - re-generate it now
        if (ueberConsumer != null) {
            List<Entitlement> ueberEntitlement 
                = entitlementCurator.listByConsumer(ueberConsumer);
            poolManager.regenerateCertificatesOf(ueberEntitlement.get(0), true);            
            return entitlementCertCurator.listForConsumer(ueberConsumer).get(0); 
        }
        
        try {            
            Product ueberProduct = createUeberProduct(o);            
            createUeberSubscription(o, ueberProduct);            
            poolManager.refreshPools(o);            
            Consumer consumer = createUeberConsumer(principal, o);
            
            List<Pool> ueberPool = poolCurator.listAvailableEntitlementPools(
                null, o, ueberProduct.getId(), null, false, false);
            
            return generateUeberCertificate(consumer, ueberPool);
        } 
        catch (Exception e) {
            log.error("Problem generating ueber cert for owner: " + o.getKey(), e);
            throw new BadRequestException(i18n.tr(
                "Problem generating ueber cert for owner {0}", e));            
        }        
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/uebercert")
    public EntitlementCertificate getUeberCertificate(@Context Principal principal, 
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey) {
        
        Owner o = findOwner(ownerKey);        
        if (o == null) {
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", ownerKey));
        }
        
        Consumer ueberConsumer = consumerCurator.findByName(UEBER_CERT_CONSUMER);
        if (ueberConsumer == null) {
            throw new NotFoundException(i18n.tr(
                "ueber certificate for owner {0} was not found. Please generate one.", 
                o.getKey()));
        }

        // ueber consumer has only one entitlement associated with it
        List<EntitlementCertificate> ueberCertificate 
            = entitlementCertCurator.listForConsumer(ueberConsumer);
        
        return ueberCertificate.get(0);
    }

    private EntitlementCertificate generateUeberCertificate(Consumer consumer,
        List<Pool> ueberPool) throws EntitlementRefusedException {
        Entitlement e = poolManager.ueberCertEntitlement(consumer, ueberPool.get(0), 1);
        return (EntitlementCertificate) e.getCertificates().toArray()[0];
    }

    private Consumer createUeberConsumer(Principal principal, Owner o) {        
        ConsumerType type = lookupConsumerType(ConsumerTypeEnum.SYSTEM.toString());
        Consumer consumer = consumerCurator.create(new Consumer(
            UEBER_CERT_CONSUMER, 
            principal.getUsername(), 
            o, 
            type));
        return consumer;
    }

    private void createUeberSubscription(Owner o, Product ueberProduct) {
        Subscription subscription = new Subscription(o, ueberProduct, 
            new HashSet<Product>(), 1L, now(), hundredYearsFromNow(), now());
        subService.createSubscription(subscription);
    }

    private Product createUeberProduct(Owner o) {
        Product ueberProduct = prodAdapter.createProduct(
            new Product(null, o.getKey() + "_ueber_product", 1L));
        
        Content ueberContent = contentCurator.create(new Content(
            "ueber_content", idGenerator.generateId(), 
            ueberProduct.getId() + "_ueber_content", "yum", "Custom", 
            "/" + o.getKey(), ""));
        
        ProductContent productContent =
            new ProductContent(ueberProduct, ueberContent, true);
        ueberProduct.getProductContent().add(productContent);
        return ueberProduct;
    }

    private Date now() {
        Calendar now = Calendar.getInstance();
        Date currentTime = now.getTime();
        return currentTime;
    }

    private Date hundredYearsFromNow() {
        Calendar now = Calendar.getInstance();
        now.add(Calendar.YEAR, 100);
        Date hunderedYearsFromNow = now.getTime();
        return hunderedYearsFromNow;
    }
        
    private void recordImportSuccess(Owner owner) {
        ImportRecord record = new ImportRecord(owner);
        record.recordStatus(ImportRecord.Status.SUCCESS,
            i18n.tr("{0} file imported successfully.", owner.getKey()));

        this.importRecordCurator.create(record);
    }

    private void recordImportFailure(Owner owner, Throwable error) {
        ImportRecord record = new ImportRecord(owner);
        record.recordStatus(ImportRecord.Status.FAILURE, error.getMessage());

        this.importRecordCurator.create(record);
    }

}
