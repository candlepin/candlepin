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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

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
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.lang.StringUtils;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventAdapter;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.EventCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerInfo;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.Statistic;
import org.candlepin.model.StatisticCurator;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.Paginate;
import org.candlepin.pinsetter.tasks.HealEntireOrgJob;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.resteasy.parameter.CandlepinParam;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.Importer;
import org.candlepin.sync.ImporterException;
import org.candlepin.sync.Meta;
import org.candlepin.sync.SyncDataFormatException;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.GenericType;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import ch.qos.logback.classic.Level;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

/**
 * Owner Resource
 */
@Path("/owners")
public class OwnerResource {

    private OwnerCurator ownerCurator;
    private OwnerInfoCurator ownerInfoCurator;
    private SubscriptionCurator subscriptionCurator;
    private ActivationKeyCurator activationKeyCurator;
    private StatisticCurator statisticCurator;
    private SubscriptionServiceAdapter subService;
    private ConsumerCurator consumerCurator;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private EventAdapter eventAdapter;
    private static Logger log = LoggerFactory.getLogger(OwnerResource.class);
    private EventCurator eventCurator;
    private Importer importer;
    private ExporterMetadataCurator exportCurator;
    private ImportRecordCurator importRecordCurator;
    private PermissionBlueprintCurator permissionCurator;
    private PoolManager poolManager;
    private ConsumerTypeCurator consumerTypeCurator;
    private EntitlementCertificateCurator entitlementCertCurator;
    private EntitlementCurator entitlementCurator;
    private UeberCertificateGenerator ueberCertGenerator;
    private EnvironmentCurator envCurator;
    private CalculatedAttributesUtil calculatedAttributesUtil;
    private ContentOverrideValidator contentOverrideValidator;
    private ServiceLevelValidator serviceLevelValidator;

    private static final int FEED_LIMIT = 1000;

    @Inject
    public OwnerResource(OwnerCurator ownerCurator,
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
        PermissionBlueprintCurator permCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EntitlementCertificateCurator entitlementCertCurator,
        EntitlementCurator entitlementCurator,
        UeberCertificateGenerator ueberCertGenerator,
        EnvironmentCurator envCurator, CalculatedAttributesUtil calculatedAttributesUtil,
        ContentOverrideValidator contentOverrideValidator,
        ServiceLevelValidator serviceLevelValidator) {

        this.ownerCurator = ownerCurator;
        this.ownerInfoCurator = ownerInfoCurator;
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
        this.entitlementCertCurator = entitlementCertCurator;
        this.entitlementCurator = entitlementCurator;
        this.ueberCertGenerator = ueberCertGenerator;
        this.envCurator = envCurator;
        this.calculatedAttributesUtil = calculatedAttributesUtil;
        this.contentOverrideValidator = contentOverrideValidator;
        this.serviceLevelValidator = serviceLevelValidator;
    }

    /**
     * Retrieves a list of Owners
     *
     * @return a list of Owner objects
     * @httpcode 200
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
     * Retrieves a single Owner
     * <p>
     * <pre>
     * {
     *   "parentOwner" : null,
     *   "id" : "database_id",
     *   "key" : "admin",
     *   "displayName" : "Admin Owner",
     *   "contentPrefix" : null,
     *   "defaultServiceLevel" : null,
     *   "upstreamConsumer" : null,
     *   "logLevel" : null,
     *   "href" : "/owners/admin",
     *   "created" : [date],
     *   "updated" : [date]
     * }
     * </pre>
     *
     * @param ownerKey Owner ID.
     * @return an Owner object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("/{owner_key}")
    @Produces(MediaType.APPLICATION_JSON)
    public Owner getOwner(@PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        return findOwner(ownerKey);
    }

    /**
     * Retrieves the Owner Info for an Owner
     *
     * @param ownerKey Owner ID.
     * @return an OwnerInfo object
     * @httpcode 404
     * @httpcode 200
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
     * Creates an Owner
     *
     * @return an Owner object
     * @httpcode 400
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Owner createOwner(Owner owner) {
        Owner parent = owner.getParentOwner();
        if (parent != null && ownerCurator.find(parent.getId()) == null) {
            throw new BadRequestException(i18n.tr(
                "Could not create the Owner: {0}. Parent {1} does not exist.",
                owner, parent));
        }
        Owner toReturn = ownerCurator.create(owner);

        sink.emitOwnerCreated(owner);

        log.info("Created owner: " + owner);
        if (toReturn != null) {
            return toReturn;
        }

        throw new BadRequestException(i18n.tr(
            "Could not create the Owner: {0}", owner));
    }

    /**
     * Removes an Owner
     *
     * @httpcode 404
     * @httpcode 200
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
        List<Consumer> consumers = consumerCurator.listByOwner(owner);
        for (Consumer c : consumers) {
            log.info("Removing all entitlements for consumer: " + c);

            if (revokeCerts) {
                poolManager.revokeAllEntitlements(c);
            }
            else {
                // otherwise just remove them without touching the CRL
                poolManager.removeAllEntitlements(c);
            }
        }

        // Actual consumer deletion had to be moved out of
        // the loop above since all entitlements needed to
        // be removed before the deletion occured. This is
        // due to the sourceConsumer that was added to Pool.
        // Deleting an entitlement may result in the deletion
        // of a sub pool, which would cause issues.
        // FIXME  Perhaps this can be handled a little better.
        for (Consumer consumer : consumers) {
            // need to check if this has been removed due to a
            // parent being deleted
            // TODO: There has to be a more efficient way to do this...
            log.info("Deleting consumer: " + consumer);
            Consumer next = consumerCurator.find(consumer.getId());
            if (next != null) {
                consumerCurator.delete(next);
            }
        }

        for (ActivationKey key : activationKeyCurator
            .listByOwner(owner)) {
            log.info("Deleting activation key: " + key);
            activationKeyCurator.delete(key);
        }
        for (Environment e : owner.getEnvironments()) {
            log.info("Deleting environment: " + e.getId());
            envCurator.delete(e);
        }
        for (Subscription s : subscriptionCurator.listByOwner(owner)) {
            log.info("Deleting subscription: " + s);
            subscriptionCurator.delete(s);
        }
        for (Pool p : poolManager.listPoolsByOwner(owner)) {
            log.info("Deleting pool: " + p);
            poolManager.deletePool(p);
        }

        cleanupUeberCert(owner);

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

        for (PermissionBlueprint perm : permissionCurator.findByOwner(owner)) {
            log.info("Deleting permission: " + perm.getAccess());
            perm.getRole().getPermissions().remove(perm);
            permissionCurator.delete(perm);
        }

        log.info("Deleting owner: " + owner);
        ownerCurator.delete(owner);
    }

    /**
     * The subscription and pool created when generating a uebercert do not appear
     * in the normal list of pools/subscriptions for that owner, and so do not get
     * cleaned up by the normal operations. Instead we must check if they exist and
     * explicitly delete them.
     *
     * @param owner Owner to check for uebercert subscription and pool.
     */
    private void cleanupUeberCert(Owner owner) {
        Subscription ueberSub = subscriptionCurator.findUeberSubscription(owner);
        if (ueberSub != null) {
            subscriptionCurator.delete(ueberSub);
        }

        Pool ueberPool = poolManager.findUeberPool(owner);
        if (ueberPool != null) {
            poolManager.deletePool(ueberPool);
        }
    }

    /**
     * Retrieves the list of Entitlements for an Owner
     *
     * @param ownerKey id of the owner whose entitlements are sought.
     * @return a list of Entitlement objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/entitlements")
    public List<Entitlement> ownerEntitlements(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwner(ownerKey);

        List<Entitlement> toReturn = new LinkedList<Entitlement>();
        for (Pool pool : owner.getPools()) {
            toReturn.addAll(poolManager.findEntitlements(pool));
        }

        return toReturn;
    }

    /**
     * Heals an Owner
     * <p>
     * Starts an asynchronous healing for the given Owner. At the end of the
     * process the idea is that all of the consumers in the owned by the Owner
     * will be up to date.
     *
     * @param ownerKey id of the owner to be healed.
     * @return a JobDetail object
     * @httpcode 404
     * @httpcode 202
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/entitlements")
    public JobDetail healEntire(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        return HealEntireOrgJob.healEntireOrg(ownerKey, new Date());
    }

    /**
     * Retrieves a list of Support Levels for an Owner
     *
     * @param ownerKey id of the owner whose support levels are sought.
     * @return a set of String objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/servicelevels")
    public Set<String> ownerServiceLevels(
        @PathParam("owner_key") @Verify(value = Owner.class,
        subResource = SubResource.SERVICE_LEVELS) String ownerKey,
        @QueryParam("exempt") @DefaultValue("false") String exempt) {
        Owner owner = findOwner(ownerKey);

        // test is on the string "true" and is case insensitive.
        return poolManager.retrieveServiceLevelsForOwner(owner,
            Boolean.parseBoolean(exempt));
    }

    /**
     * Retrieves a list of Activation Keys for an Owner
     *
     * @param ownerKey id of the owner whose keys are sought.
     * @return a list of Activation Key objects
     * @httpcode 404
     * @httpcode 200
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
     * Creates an Activation Key for the Owner
     *
     * @param ownerKey id of the owner whose keys are sought.
     * @return an Activation Key object
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/activation_keys")
    public ActivationKey createActivationKey(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        ActivationKey activationKey) {

        Owner owner = findOwner(ownerKey);
        activationKey.setOwner(owner);

        if (StringUtils.isBlank(activationKey.getName())) {
            throw new BadRequestException(
                i18n.tr("Must provide a name for activation key."));
        }

        String testName = activationKey.getName().replace("-", "0")
                          .replace("_", "0");
        if (!testName.matches("[a-zA-Z0-9]*")) {
            throw new BadRequestException(
                i18n.tr("The activation key name ''{0}'' must be alphanumeric or " +
                    "include the characters '-' or '_'", activationKey.getName()));
        }

        if (activationKeyCurator.lookupForOwner(activationKey.getName(), owner) != null) {
            throw new BadRequestException(
                i18n.tr("The activation key name ''{0}'' is already in use for owner {1}",
                    activationKey.getName(), ownerKey));
        }

        if (activationKey.getContentOverrides() != null) {
            contentOverrideValidator.validate(activationKey.getContentOverrides());
        }

        serviceLevelValidator.validate(owner, activationKey.getServiceLevel());

        ActivationKey newKey = activationKeyCurator.create(activationKey);
        sink.emitActivationKeyCreated(newKey);

        return newKey;
    }

    /**
     * Creates an Environment for an Owner
     *
     * @return an Environment object
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/environments")
    public Environment createEnv(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey, Environment env) {
        Owner owner = findOwner(ownerKey);
        env.setOwner(owner);
        env = envCurator.create(env);
        return env;
    }

    /**
     * Retrieves a list of Environments for an Owner
     *
     * @param envName Optional environment name filter to search for.
     * @return a list of Environment objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/environments")
    @Wrapped(element = "environments")
    public List<Environment> listEnvironments(@PathParam("owner_key")
        @Verify(Owner.class) String ownerKey, @QueryParam("name") String envName) {
        Owner owner = findOwner(ownerKey);
        List<Environment> envs = null;
        if (envName == null) {
            envs = envCurator.listForOwner(owner);
        }
        else {
            envs = envCurator.listForOwnerByName(owner, envName);
        }
        return envs;
    }

    /**
     * Sets the Log Level for an Owner
     *
     * @param ownerKey
     * @param level
     * @return an Owner object
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/log")
    public Owner setLogLevel(@PathParam("owner_key") String ownerKey,
        @QueryParam("level") @DefaultValue("DEBUG") String level) {
        Owner owner = findOwner(ownerKey);
        level = level.toUpperCase();

        List<String> acceptedLevels = new ArrayList<String>();
        acceptedLevels.add(Level.ALL.toString());
        acceptedLevels.add(Level.TRACE.toString());
        acceptedLevels.add(Level.DEBUG.toString());
        acceptedLevels.add(Level.INFO.toString());
        acceptedLevels.add(Level.WARN.toString());
        acceptedLevels.add(Level.ERROR.toString());
        acceptedLevels.add(Level.OFF.toString());

        if (!acceptedLevels.contains(level)) {
            throw new BadRequestException(i18n.tr("{0} is not a valid log level", level));
        }

        owner.setLogLevel(level);
        ownerCurator.merge(owner);
        return owner;
    }

    /**
     * Remove the Log Level of an Owner
     *
     * @param ownerKey
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/log")
    public void deleteLogLevel(@PathParam("owner_key") String ownerKey) {
        Owner owner = findOwner(ownerKey);
        owner.setLogLevel(null);
        ownerCurator.merge(owner);
    }

    /**
     * Retrieve a list of Consumers for the Owner
     *
     * @param ownerKey id of the owner whose consumers are sought.
     * @return a list of Consumer objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/consumers")
    @Paginate
    public List<Consumer> listConsumers(
            @PathParam("owner_key")
            @Verify(value = Owner.class,
                subResource = SubResource.CONSUMERS) String ownerKey,
            @QueryParam("username") String userName,
            @QueryParam("type") Set<String> typeLabels,
            @QueryParam("uuid") @Verify(value = Consumer.class, nullable = true)
                List<String> uuids,
            @QueryParam("hypervisor_id") List<String> hypervisorIds,
            @QueryParam("fact") @CandlepinParam(type = KeyValueParameter.class)
                List<KeyValueParameter> attrFilters,
            @Context PageRequest pageRequest) {
        Owner owner = findOwner(ownerKey);
        List<ConsumerType> types = null;
        if (typeLabels != null && !typeLabels.isEmpty()) {
            types = consumerTypeCurator.lookupConsumerTypes(typeLabels);
        }

        Page<List<Consumer>> page = consumerCurator.searchOwnerConsumers(
            owner, userName, types, uuids, hypervisorIds, attrFilters, pageRequest);

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, page);
        return page.getPageData();
    }


    /**
     * Retrieves a list of Pools for an Owner
     *
     * @param ownerKey id of the owner whose entitlement pools are sought.
     * @return a list of Pool objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/pools")
    @Paginate
    public List<Pool> listPools(
        @PathParam("owner_key")
            @Verify(value = Owner.class, subResource = SubResource.POOLS) String ownerKey,
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("activation_key") String activationKeyName,
        @QueryParam("product") String productId,
        @QueryParam("listall") @DefaultValue("false") boolean listAll,
        @QueryParam("activeon") String activeOn,
        @QueryParam("attribute") @CandlepinParam(type = KeyValueParameter.class)
            List<KeyValueParameter> attrFilters,
        @Context Principal principal,
        @Context PageRequest pageRequest) {

        Owner owner = findOwner(ownerKey);

        Date activeOnDate = new Date();
        if (activeOn != null) {
            activeOnDate = ResourceDateParser.parseDateString(activeOn);
        }

        Consumer c = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("Unit: {0} not found",
                    consumerUuid));
            }

            if (!c.getOwner().getId().equals(owner.getId())) {
                throw new BadRequestException(
                    "Consumer specified does not belong to owner on path");
            }

            if (!principal.canAccess(c, SubResource.NONE, Access.READ_ONLY)) {
                throw new ForbiddenException(i18n.tr("User {0} cannot access consumer {1}",
                    principal.getPrincipalName(), c.getUuid()));
            }
        }

        ActivationKey key = null;
        if (activationKeyName != null) {
            key = activationKeyCurator.lookupForOwner(activationKeyName, owner);
            if (key == null) {
                throw new BadRequestException(
                    i18n.tr("ActivationKey with id {0} could not be found.",
                        activationKeyName));
            }
        }

        // Process the filters passed for the attributes
        PoolFilterBuilder poolFilters = new PoolFilterBuilder();
        for (KeyValueParameter filterParam : attrFilters) {
            poolFilters.addAttributeFilter(filterParam.key(), filterParam.value());
        }

        Page<List<Pool>> page = poolManager.listAvailableEntitlementPools(c, key, owner,
            productId, activeOnDate, true, listAll, poolFilters, pageRequest);
        List<Pool> poolList = page.getPageData();

        if (c != null) {
            for (Pool p : poolList) {
                p.setCalculatedAttributes(
                    calculatedAttributesUtil.buildCalculatedAttributes(p, c, activeOnDate));
            }
        }

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, page);
        return poolList;
    }

    /**
     * Creates a Subscription for an Owner
     *
     * @return a Subscription object
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Retrieves an Event Atom Feed for an owner
     *
     * @return a Feed object
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Retrieves a list of Events for an Owner
     *
     * @return a list of Event objects
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Retrieves a list of Subscriptions for an Owner
     *
     * @return a list of Subscription objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")
    public List<Subscription> getSubscriptions(
        @PathParam("owner_key") @Verify(value = Owner.class,
            subResource = SubResource.SUBSCRIPTIONS) String ownerKey) {
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
            throw new NotFoundException(i18n.tr("No such unit: {0}",
                consumerUuid));
        }
        return consumer;
    }

    /**
     * Updates an Owner
     * <p>
     * To un-set the defaultServiceLevel for an owner, submit an empty string.
     *
     * @param key
     * @param owner
     * @return an Owner object
     * @httpcode 404
     * @httpcode 200
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}")
    @Transactional
    public Owner updateOwner(@PathParam("owner_key") @Verify(Owner.class) String key,
        Owner owner) {
        Owner toUpdate = findOwner(key);
        log.debug("Updating owner: " + key);

        if (owner.getDisplayName() != null) {
            toUpdate.setDisplayName(owner.getDisplayName());
        }
        if (owner.getParentOwner() != null) {
            toUpdate.setParentOwner(owner.getParentOwner());
        }

        // Make sure we don't wipe out the service level if none was included in the
        // request. Interpret empty string as a signal to clear the default service
        // level.
        if (owner.getDefaultServiceLevel() != null) {
            if (owner.getDefaultServiceLevel().equals("")) {
                toUpdate.setDefaultServiceLevel(null);
            }
            else {
                serviceLevelValidator.validate(toUpdate, owner.getDefaultServiceLevel());
                toUpdate.setDefaultServiceLevel(owner.getDefaultServiceLevel());
            }
        }

        ownerCurator.merge(toUpdate);
        Event e = eventFactory.ownerModified(owner);
        sink.sendEvent(e);
        return toUpdate;
    }

    /**
     * Refreshes the Pools for an Owner
     * <p>
     * 'Tickle' an owner to have all of their entitlement pools synced with
     * their subscriptions. This method (and the one below may not be entirely
     * RESTful, as the updated data is not supplied as an argument.
     *
     * @param ownerKey unique id key of the owner whose pools should be updated
     * @return a JobDetail object
     * @httpcode 404
     * @httpcode 202
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")
    public JobDetail refreshPools(
        // TODO: Can we verify with autocreate?
        @PathParam("owner_key") String ownerKey,
        @QueryParam("auto_create_owner") @DefaultValue("false") Boolean autoCreateOwner,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

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

        return RefreshPoolsJob.forOwner(owner, lazyRegen);
    }

    /**
     * Updates a Subscription for an Owner
     *
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Removes Imports for an Owner
     * <p>
     * Cleans out all imported subscriptions and triggers a background refresh pools.
     * Link to an upstream distributor is removed for the owner, so they can import from
     * another distributor. Other owners can also now import the manifests originally
     * used in this owner.
     * <p>
     * This call does not differentiate between any specific import, it just
     * destroys all subscriptions with an upstream pool ID, essentially anything from
     * an import. Custom subscriptions will be left alone.
     * <p>
     * Imports do carry rules and product information which is global to the candlepin
     * server. This import data is *not* undone, we assume that updates to this data
     * can be safely kept.
     *
     * @return a JobDetail object
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("{owner_key}/imports")
    @Produces(MediaType.APPLICATION_JSON)
    public JobDetail undoImports(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @Context Principal principal) {

        Owner owner = findOwner(ownerKey);
        log.info("Deleting all subscriptions from manifests for owner: " + ownerKey);

        // In this situation we know we should be querying the curator rather than the
        // service:
        List<Subscription> subs = subscriptionCurator.listByOwner(owner);
        for (Subscription sub : subs) {

            // Subscriptions from manifests will have an upstream pool ID, so only
            // these should be deleted. Anything else is likely a custom subscription
            // and needs to be left alone.
            if (sub.getUpstreamPoolId() != null) {
                subscriptionCurator.delete(sub);
            }
        }

        // Clear out upstream ID so owner can import from other distributors:
        UpstreamConsumer uc = owner.getUpstreamConsumer();
        owner.setUpstreamConsumer(null);

        ExporterMetadata metadata = exportCurator.lookupByTypeAndOwner(
            ExporterMetadata.TYPE_PER_USER, owner);
        if (metadata == null) {
            throw new NotFoundException("No import found for owner " + ownerKey);
        }
        exportCurator.delete(metadata);

        this.recordManifestDeletion(owner, principal.getUsername(), uc);

        // Refresh pools to cleanup entitlements:
        return RefreshPoolsJob.forOwner(owner, false);
    }

    /**
     * Imports a Manifest to the Owner
     *
     * @httpcode 400
     * @httpcode 404
     * @httpcode 500
     * @httpcode 200
     * @httpcode 409
     */
    @POST
    @Path("{owner_key}/imports")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void importManifest(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("force") String[] overrideConflicts, MultipartInput input) {

        if (overrideConflicts.length == 1) {
            /*
             * For backward compatibility, look for force=true and if found,
             * treat it just like what it used to mean, ignore an old manifest
             * creation date.
             */
            if (overrideConflicts[0].equalsIgnoreCase("true")) {
                overrideConflicts = new String [] { "MANIFEST_OLD" };
            }
            else if (overrideConflicts[0].equalsIgnoreCase("false")) {
                overrideConflicts = new String [] {};
            }
        }
        if (log.isDebugEnabled()) {
            for (String s : overrideConflicts) {
                log.debug("Forcing conflict if encountered: " + s);
            }
        }

        ConflictOverrides overrides = null;
        try {
            overrides = new ConflictOverrides(overrideConflicts);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException(i18n.tr("Unknown conflict to force"));
        }

        String filename = "";
        Owner owner = findOwner(ownerKey);
        Map<String, Object> data = new HashMap<String, Object>();
        try {
            InputPart part = input.getParts().get(0);
            MultivaluedMap<String, String> headers = part.getHeaders();
            String contDis = headers.getFirst("Content-Disposition");
            StringTokenizer st = new StringTokenizer(contDis, ";");
            while (st.hasMoreTokens()) {
                String entry = st.nextToken().trim();
                if (entry.startsWith("filename")) {
                    filename = entry.substring(entry.indexOf("=") + 2, entry.length() - 1);
                    break;
                }
            }
            File archive = part.getBody(new GenericType<File>() {
            });
            log.info("Importing archive " + archive.getAbsolutePath() +
                " for owner " + owner.getDisplayName());
            data = importer.loadExport(owner, archive, overrides);

            sink.emitImportCreated(owner);
            recordImportSuccess(owner, data, overrides, filename);
        }
        catch (IOException e) {
            recordImportFailure(owner, data, e, filename);
            throw new IseException(i18n.tr("Error reading export archive"), e);
        }
        // These come back with internationalized messages, so we can transfer:
        catch (SyncDataFormatException e) {
            recordImportFailure(owner, data, e, filename);
            throw new BadRequestException(e.getMessage(), e);
        }
        catch (ImporterException e) {
            recordImportFailure(owner, data, e, filename);
            throw new IseException(e.getMessage(), e);
        }
        // Grab candlepin exceptions to record the error and then rethrow
        // to pass on the http return code
        catch (CandlepinException e) {
            recordImportFailure(owner, data, e, filename);
            throw e;
        }
        finally {
            log.info("Import attempt completed for owner " + owner.getDisplayName());
        }
    }

    /**
     * Retrieves a list of Import Records for an Owner
     *
     * @return a list of ImportRecord objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/imports")
    public List<ImportRecord> getImports(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwner(ownerKey);

        return this.importRecordCurator.findRecords(owner);
    }

    /**
     * Retrieves a list of Statistics for an Owner
     *
     * @return a list of Statistic objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Retrieves a list of Statistics for an Owner
     * <p>
     * By Type
     *
     * @return a list of Statistic objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Retrieves a list of Statistics for an Owner
     * <p>
     * By Types
     *
     * @return a list of Statistic objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Creates an Ueber Entitlement Certificate
     *
     * @return an EntitlementCertificate object
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
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

        Consumer ueberConsumer =
            consumerCurator.findByName(o, Consumer.UEBER_CERT_CONSUMER);

        // ueber cert has already been generated - re-generate it now
        if (ueberConsumer != null) {
            List<Entitlement> ueberEntitlement
                = entitlementCurator.listByConsumer(ueberConsumer);
            // Immediately revoke and regenerate ueber certificates:
            poolManager.regenerateCertificatesOf(ueberEntitlement.get(0), true, false);
            return entitlementCertCurator.listForConsumer(ueberConsumer).get(0);
        }

        try {
            return ueberCertGenerator.generate(o, principal);
        }
        catch (Exception e) {
            log.error("Problem generating ueber cert for owner: " + o.getKey(), e);
            throw new BadRequestException(i18n.tr(
                "Problem generating ueber cert for owner {0}", e));
        }
    }

    /**
     * Retrieves the Ueber Entitlement Certificate
     *
     * @return an EntitlementCertificate object
     * @httpcode 404
     * @httpcode 200
     */
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

        Consumer ueberConsumer =
            consumerCurator.findByName(o, Consumer.UEBER_CERT_CONSUMER);

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

    /**
     * Retrieves a list of Upstream Consumers for an Owner
     *
     * @return a list of UpstreamConsumer objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/upstream_consumers")
    public List<UpstreamConsumer> getUpstreamConsumers(@Context Principal principal,
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey) {
        Owner o = findOwner(ownerKey);
        if (o == null) {
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", ownerKey));
        }

        // returning as a list for future proofing. today we support one, but
        // users of this api want to protect against having to change their code
        // when multiples are supported.
        UpstreamConsumer upstream = o.getUpstreamConsumer();

        List<UpstreamConsumer> results = new ArrayList<UpstreamConsumer>(1);
        results.add(upstream);
        return results;
    }

    /**
     * Retrieves a list of Hypervisors for an Owner
     *
     * @param ownerKey
     * @param hypervisorIds
     * @return a list of Consumer objects
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{owner_key}/hypervisors")
    public List<Consumer> getHypervisors(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("hypervisor_id") List<String> hypervisorIds) {
        if (hypervisorIds == null || hypervisorIds.isEmpty()) {
            return consumerCurator.getHypervisorsForOwner(ownerKey);
        }
        return consumerCurator.getHypervisorsBulk(hypervisorIds, ownerKey);
    }

    private void recordImportSuccess(Owner owner, Map data,
        ConflictOverrides forcedConflicts, String filename) {

        ImportRecord record = new ImportRecord(owner);
        Meta meta = (Meta) data.get("meta");
        if (meta != null) {
            record.setGeneratedBy(meta.getPrincipalName());
            record.setGeneratedDate(meta.getCreated());
        }
        record.setUpstreamConsumer(createImportUpstreamConsumer(owner, null));
        record.setFileName(filename);

        String msg = i18n.tr("{0} file imported successfully.", owner.getKey());
        if (!forcedConflicts.isEmpty()) {
            msg = i18n.tr("{0} file imported forcibly", owner.getKey());
        }

        record.recordStatus(ImportRecord.Status.SUCCESS, msg);

        this.importRecordCurator.create(record);
    }

    private void recordImportFailure(Owner owner, Map data, Throwable error,
        String filename) {
        ImportRecord record = new ImportRecord(owner);
        Meta meta = (Meta) data.get("meta");
        log.error("Recording import failure", error);
        if (meta != null) {
            record.setGeneratedBy(meta.getPrincipalName());
            record.setGeneratedDate(meta.getCreated());
        }
        record.setUpstreamConsumer(createImportUpstreamConsumer(owner, null));
        record.setFileName(filename);

        record.recordStatus(ImportRecord.Status.FAILURE, error.getMessage());

        this.importRecordCurator.create(record);
    }

    private void recordManifestDeletion(Owner owner, String username,
        UpstreamConsumer uc) {
        ImportRecord record = new ImportRecord(owner);
        record.setGeneratedBy(username);
        record.setGeneratedDate(new Date());
        String msg = i18n.tr("Subscriptions deleted by {0}", username);
        record.recordStatus(ImportRecord.Status.DELETE, msg);
        record.setUpstreamConsumer(createImportUpstreamConsumer(owner, uc));

        this.importRecordCurator.create(record);
    }

    private ImportUpstreamConsumer createImportUpstreamConsumer(Owner owner,
        UpstreamConsumer uc) {
        ImportUpstreamConsumer iup = null;
        if (uc == null) {
            uc = owner.getUpstreamConsumer();
        }
        if (uc != null) {
            iup = new ImportUpstreamConsumer();
            iup.setOwnerId(uc.getOwnerId());
            iup.setName(uc.getName());
            iup.setUuid(uc.getUuid());
            iup.setType(uc.getType());
            iup.setWebUrl(uc.getWebUrl());
            iup.setApiUrl(uc.getApiUrl());
        }
        return iup;
    }
}
