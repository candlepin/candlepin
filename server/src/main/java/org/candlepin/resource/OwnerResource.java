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

import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventAdapter;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.CandlepinException;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.common.paging.Paginate;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.EventCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerInfo;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pinsetter.tasks.HealEntireOrgJob;
import org.candlepin.pinsetter.tasks.ImportJob;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.candlepin.pinsetter.tasks.UndoImportsJob;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.EntitlementFinderUtil;
import org.candlepin.resource.util.ResolverUtil;
import org.candlepin.resteasy.DateFormat;
import org.candlepin.resteasy.parameter.CandlepinParam;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.ContentAccessCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.ImporterException;
import org.candlepin.sync.SyncDataFormatException;
import org.candlepin.sync.file.ManifestFileServiceException;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.persistence.PersistenceException;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

/**
 * Owner Resource
 */
@Path("/owners")
@Api(value = "owners", authorizations = { @Authorization("basic") })
public class OwnerResource {

    private static Logger log = LoggerFactory.getLogger(OwnerResource.class);

    private static final int FEED_LIMIT = 1000;

    private OwnerCurator ownerCurator;
    private OwnerInfoCurator ownerInfoCurator;
    private ActivationKeyCurator activationKeyCurator;
    private OwnerServiceAdapter ownerService;
    private ConsumerCurator consumerCurator;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private EventAdapter eventAdapter;
    private EventCurator eventCurator;
    private ManifestManager manifestManager;
    private ExporterMetadataCurator exportCurator;
    private ImportRecordCurator importRecordCurator;
    private PoolManager poolManager;
    private OwnerManager ownerManager;
    private ConsumerTypeCurator consumerTypeCurator;
    private EntitlementCertificateCurator entitlementCertCurator;
    private EntitlementCurator entitlementCurator;
    private UeberCertificateCurator ueberCertCurator;
    private UeberCertificateGenerator ueberCertGenerator;
    private EnvironmentCurator envCurator;
    private CalculatedAttributesUtil calculatedAttributesUtil;
    private ContentOverrideValidator contentOverrideValidator;
    private ServiceLevelValidator serviceLevelValidator;
    private Configuration config;
    private ResolverUtil resolverUtil;
    private ProductManager productManager;
    private ContentManager contentManager;
    private ConsumerTypeValidator consumerTypeValidator;

    @Inject
    public OwnerResource(OwnerCurator ownerCurator,
        ActivationKeyCurator activationKeyCurator,
        ConsumerCurator consumerCurator,
        I18n i18n,
        EventSink sink,
        EventFactory eventFactory,
        EventCurator eventCurator,
        EventAdapter eventAdapter,
        ManifestManager manifestManager,
        PoolManager poolManager,
        OwnerManager ownerManager,
        ExporterMetadataCurator exportCurator,
        OwnerInfoCurator ownerInfoCurator,
        ImportRecordCurator importRecordCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EntitlementCertificateCurator entitlementCertCurator,
        EntitlementCurator entitlementCurator,
        UeberCertificateCurator ueberCertCurator,
        UeberCertificateGenerator ueberCertGenerator,
        EnvironmentCurator envCurator,
        CalculatedAttributesUtil calculatedAttributesUtil,
        ContentOverrideValidator contentOverrideValidator,
        ServiceLevelValidator serviceLevelValidator,
        OwnerServiceAdapter ownerService,
        Configuration config,
        ResolverUtil resolverUtil,
        ProductManager productManager,
        ContentManager contentManager,
        ConsumerTypeValidator consumerTypeValidator) {

        this.ownerCurator = ownerCurator;
        this.ownerInfoCurator = ownerInfoCurator;
        this.activationKeyCurator = activationKeyCurator;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.eventCurator = eventCurator;
        this.exportCurator = exportCurator;
        this.importRecordCurator = importRecordCurator;
        this.poolManager = poolManager;
        this.manifestManager = manifestManager;
        this.ownerManager = ownerManager;
        this.eventAdapter = eventAdapter;
        this.consumerTypeCurator = consumerTypeCurator;
        this.entitlementCertCurator = entitlementCertCurator;
        this.entitlementCurator = entitlementCurator;
        this.ueberCertCurator = ueberCertCurator;
        this.ueberCertGenerator = ueberCertGenerator;
        this.envCurator = envCurator;
        this.calculatedAttributesUtil = calculatedAttributesUtil;
        this.contentOverrideValidator = contentOverrideValidator;
        this.serviceLevelValidator = serviceLevelValidator;
        this.ownerService = ownerService;
        this.config = config;
        this.resolverUtil = resolverUtil;
        this.productManager = productManager;
        this.contentManager = contentManager;
        this.consumerTypeValidator = consumerTypeValidator;
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
    @ApiOperation(notes = "Retrieves a list of Owners", value = "List Owners", response = Owner.class,
        responseContainer = "list")
    public CandlepinQuery<Owner> list(@QueryParam("key") String keyFilter) {
        return keyFilter != null ?
            this.ownerCurator.lookupByKeys(Arrays.asList(keyFilter)) :
            this.ownerCurator.listAll();
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
    @ApiOperation(notes = "Retrieves a single Owner", value = "Get Owner")
    @ApiResponses({ @ApiResponse(code = 404, message = "An owner not found") })
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
    @ApiOperation(notes = "Retrieves the Owner Info for an Owner", value = "Get Owner Info")
    @ApiResponses({ @ApiResponse(code = 404, message = "An owner not found") })
    public OwnerInfo getOwnerInfo(@PathParam("owner_key")
        @Verify(value = Owner.class, subResource = SubResource.CONSUMERS) String ownerKey) {
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
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(notes = "Creates an Owner", value = "Create Owner")
    @ApiResponses({ @ApiResponse(code = 400, message = "Invalid owner specified in body") })
    public Owner createOwner(@ApiParam(name = "owner", required = true) Owner owner) {
        Owner parent = owner.getParentOwner();
        if (parent != null && ownerCurator.find(parent.getId()) == null) {
            throw new BadRequestException(i18n.tr(
                "Could not create the Owner: {0}. Parent {1} does not exist.", owner, parent));
        }
        if (StringUtils.isBlank(owner.getContentAccessModeList())) {
            owner.setContentAccessModeList(
                ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE);
            owner.setContentAccessMode(
                ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE);
        }
        if (StringUtils.isBlank(owner.getContentAccessMode())) {
            throw new BadRequestException(
                i18n.tr("You must assign a Content Access Mode from the mode list."));
        }

        if (!owner.isAllowedContentAccessMode(owner.getContentAccessMode())) {
            throw new BadRequestException(
                i18n.tr("The content access mode is not allowed for this owner."));
        }

        Owner created = ownerCurator.create(owner);
        if (created == null) {
            throw new BadRequestException(i18n.tr("Could not create the Owner: {0}", owner));
        }

        log.info("Created owner: {}", created);
        sink.emitOwnerCreated(created);

        return created;
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
    @ApiOperation(notes = "Removes an Owner", value = "Delete Owner")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public void deleteOwner(@PathParam("owner_key") String ownerKey,
        @QueryParam("revoke") @DefaultValue("true") boolean revoke,
        @QueryParam("force") @DefaultValue("false") boolean force) {
        Owner owner = findOwner(ownerKey);
        Event event = eventFactory.ownerDeleted(owner);

        if (!force && consumerCurator.doesShareConsumerExist(owner)) {
            throw new BadRequestException(
                i18n.tr("owner ''{0}'' cannot be deleted while there is a share consumer. " +
                    "You may use 'force' to bypass.", owner.getKey()));
        }

        try {
            ownerManager.cleanupAndDelete(owner, revoke);
        }
        catch (PersistenceException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                throw new ConflictException(e.getMessage(), e);
            }
            else {
                throw e;
            }
        }

        sink.queueEvent(event);
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
    @Paginate
    @ApiOperation(notes = "Retrieves the list of Entitlements for an Owner",
        value = "List Owner Entitlements")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public List<Entitlement> ownerEntitlements(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("product") String productId,
        @QueryParam("matches") String matches,
        @QueryParam("attribute") @CandlepinParam(type = KeyValueParameter.class)
        List<KeyValueParameter> attrFilters,
        @Context PageRequest pageRequest) {

        Owner owner = findOwner(ownerKey);

        EntitlementFilterBuilder filters = EntitlementFinderUtil.createFilter(matches, attrFilters);
        Page<List<Entitlement>> entitlementsPage = entitlementCurator
            .listByOwner(owner, productId, filters, pageRequest);

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, entitlementsPage);

        return entitlementsPage.getPageData();
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
    @Consumes(MediaType.WILDCARD)
    @Path("{owner_key}/entitlements")
    @ApiOperation(notes = "Starts an asynchronous healing for the given Owner." +
        " At the end of the process the idea is that all of the consumers " +
        "in the owned by the Owner will be up to date.", value = "Heal owner")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public JobDetail healEntire(
        @ApiParam("ownerKey id of the owner to be healed.")
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
    @ApiOperation(notes = "Retrieves a list of Support Levels for an Owner", value = "Get Service Levels")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public Set<String> ownerServiceLevels(
        @ApiParam("ownerKey id of the owner whose support levels are sought.")
        @PathParam("owner_key") @Verify(value = Owner.class,
        subResource = SubResource.SERVICE_LEVELS) String ownerKey,
        @Context Principal principal,
        @QueryParam("exempt") @DefaultValue("false") String exempt) {
        Owner owner = findOwner(ownerKey);

        if (principal.getType().equals("consumer")) {
            Consumer c = consumerCurator.findByUuid(principal.getName());
            if (c.isDev()) {
                Set<String> result = new HashSet<String>();
                result.add("");
                return result;
            }
        }
        // test is on the string "true" and is case insensitive.
        return poolManager.retrieveServiceLevelsForOwner(owner, Boolean.parseBoolean(exempt));
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
    @ApiOperation(notes = "Retrieves a list of Activation Keys for an Owner", value = "Owner Activation Keys")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public List<ActivationKey> ownerActivationKeys(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("name") String keyName) {
        Owner owner = findOwner(ownerKey);

        if (keyName == null) {
            return this.activationKeyCurator.listByOwner(owner);
        }
        else {
            List<ActivationKey> results = new ArrayList<ActivationKey>();
            results.add(activationKeyCurator.lookupForOwner(keyName, owner));
            return results;
        }
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
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/activation_keys")
    @ApiOperation(notes = "Creates an Activation Key for the Owner", value = "Create Activation Key")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found"),
        @ApiResponse(code = 400, message = "Invalid activation key") })
    public ActivationKey createActivationKey(@PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @ApiParam(name = "activation_key", required = true) ActivationKey activationKey) {

        Owner owner = findOwner(ownerKey);
        activationKey.setOwner(owner);

        if (StringUtils.isBlank(activationKey.getName())) {
            throw new BadRequestException(i18n.tr("Must provide a name for activation key."));
        }

        String testName = activationKey.getName().replace("-", "0").replace("_", "0");

        if (!testName.matches("[a-zA-Z0-9]*")) {
            throw new BadRequestException(
                i18n.tr("The activation key name ''{0}'' must be alphanumeric or " +
                    "include the characters ''-'' or ''_''", activationKey.getName()));
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
    @ApiOperation(notes = "Creates an Environment for an Owner", value = "Create environment")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found")})
    public Environment createEnv(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @ApiParam(name = "environment", required = true) Environment env) {
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
    @ApiOperation(notes = "Retrieves a list of Environments for an Owner", value = "List environments")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found")})
    public List<Environment> listEnvironments(@PathParam("owner_key")
        @Verify(Owner.class) String ownerKey,
        @ApiParam("Environment name filter to search for.")
        @QueryParam("name") String envName) {
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
    @Consumes(MediaType.WILDCARD)
    @Path("{owner_key}/log")
    @ApiOperation(notes = "Sets the Log Level for an Owner", value = "Set Log Level")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found")})
    public Owner setLogLevel(@PathParam("owner_key") String ownerKey,
        @QueryParam("level") @DefaultValue("DEBUG")
        @ApiParam(allowableValues = "ALL, TRACE, DEBUG, INFO, WARN, ERROR, OFF") String level) {
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
    @ApiOperation(notes = "Remove the Log Level of an Owner", value = "Remove Log Level")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found")})
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
    @SuppressWarnings("checkstyle:indentation")
    @ApiOperation(notes = "Retrieve a list of Consumers for the Owner", value = "List Consumers",
        response = Consumer.class, responseContainer = "list")
    @ApiResponses({
        @ApiResponse(code = 404, message = "Owner not found"),
        @ApiResponse(code = 400, message = "Invalid request")
    })
    public CandlepinQuery<Consumer> listConsumers(
        @PathParam("owner_key")
        @Verify(value = Owner.class, subResource = SubResource.CONSUMERS) String ownerKey,
        @QueryParam("username") String userName,
        @QueryParam("type") Set<String> typeLabels,
        @QueryParam("uuid") @Verify(value = Consumer.class, nullable = true) List<String> uuids,
        @QueryParam("hypervisor_id") List<String> hypervisorIds,
        @QueryParam("fact") @CandlepinParam(type = KeyValueParameter.class)
            List<KeyValueParameter> attrFilters,
        @QueryParam("sku") List<String> skus,
        @QueryParam("subscription_id") List<String> subscriptionIds,
        @QueryParam("contract") List<String> contracts,
        @Context PageRequest pageRequest) {

        Owner owner = findOwner(ownerKey);
        List<ConsumerType> types = consumerTypeValidator.findAndValidateTypeLabels(typeLabels);

        return this.consumerCurator.searchOwnerConsumers(
            owner, userName, types, uuids, hypervisorIds, attrFilters, skus,
            subscriptionIds, contracts);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/consumers/count")
    @SuppressWarnings("checkstyle:indentation")
    @ApiOperation(notes = "Retrieve a count of Consumers for the Owner", value = "consumers count")
    @ApiResponses({
        @ApiResponse(code = 404, message = "Owner not found"),
        @ApiResponse(code = 400, message = "Invalid request")
    })
    public int countConsumers(
        @PathParam("owner_key")
        @Verify(value = Owner.class, subResource = SubResource.CONSUMERS) String ownerKey,
        @QueryParam("type") Set<String> typeLabels,
        @QueryParam("sku") List<String> skus,
        @QueryParam("subscription_id") List<String> subscriptionIds,
        @QueryParam("contract") List<String> contracts) {

        findOwner(ownerKey);
        consumerTypeValidator.findAndValidateTypeLabels(typeLabels);

        return consumerCurator.countConsumers(ownerKey, typeLabels, skus, subscriptionIds, contracts);
    }

    /**
     * Retrieves a list of Pools for an Owner
     *
     * @param ownerKey id of the owner whose entitlement pools are sought.
     * @param matches Find pools matching the given pattern in a variety of fields.
     * * and ? wildcards are supported.
     * @return a list of Pool objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/pools")
    @Paginate
    @SuppressWarnings("checkstyle:indentation")
    @ApiOperation(notes = "Retrieves a list of Pools for an Owner", value = "List Pools")
    @ApiResponses({
        @ApiResponse(code = 404, message = "Owner not found"),
        @ApiResponse(code = 400, message = "Invalid request")
    })
    public List<Pool> listPools(
        @PathParam("owner_key") @Verify(value = Owner.class, subResource = SubResource.POOLS) String ownerKey,
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("activation_key") String activationKeyName,
        @QueryParam("product") String productId,
        @QueryParam("subscription") String subscriptionId,
        @ApiParam("Include pools that are not suited to the unit's facts.")
        @QueryParam("listall") @DefaultValue("false") boolean listAll,
        @ApiParam("Date to use as current time for lookup criteria. Defaults" +
                " to current date if not specified.")
        @QueryParam("activeon") @DefaultValue(DateFormat.NOW) @DateFormat Date activeOn,
        @ApiParam("Find pools matching the given pattern in a variety of fields" +
                " * and ? wildcards are supported.")
        @QueryParam("matches") String matches,
        @ApiParam("The attributes to return based on the specified types.")
        @QueryParam("attribute") @CandlepinParam(type = KeyValueParameter.class)
            List<KeyValueParameter> attrFilters,
        @ApiParam("When set to true, it will add future dated pools to the result, " +
                "based on the activeon date.")
        @QueryParam("add_future") @DefaultValue("false") boolean addFuture,
        @ApiParam("When set to true, it will return only future dated pools to the result, " +
                "based on the activeon date.")
        @QueryParam("only_future") @DefaultValue("false") boolean onlyFuture,
        @Context Principal principal,
        @Context PageRequest pageRequest) {

        Owner owner = findOwner(ownerKey);

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
                    i18n.tr("ActivationKey with id {0} could not be found.", activationKeyName)
                );
            }
        }

        if (addFuture && onlyFuture) {
            throw new BadRequestException(
                i18n.tr("The flags add_future and only_future cannot be used at the same time."));
        }
        // Process the filters passed for the attributes
        PoolFilterBuilder poolFilters = new PoolFilterBuilder();
        for (KeyValueParameter filterParam : attrFilters) {
            poolFilters.addAttributeFilter(filterParam.key(), filterParam.value());
        }
        if (!StringUtils.isEmpty(matches)) {
            poolFilters.addMatchesFilter(matches);
        }

        Page<List<Pool>> page = poolManager.listAvailableEntitlementPools(
            c, key, owner, productId, subscriptionId, activeOn, listAll, poolFilters, pageRequest,
        addFuture, onlyFuture);
        List<Pool> poolList = page.getPageData();
        calculatedAttributesUtil.setCalculatedAttributes(poolList, activeOn);
        calculatedAttributesUtil.setQuantityAttributes(poolList, c, activeOn);

        // Store the page for the LinkHeaderResponseFilter
        ResteasyProviderFactory.pushContext(Page.class, page);
        return poolList;
    }

    /**
     * Retrieves an Event
     *
     * @return a Feed object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces("application/atom+xml")
    @Path("{owner_key}/atom")
    @ApiOperation(notes = "Retrieves an Event Atom Feed for an owner", value = "Get Atom Feed")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found")})
    public Feed getOwnerAtomFeed(@PathParam("owner_key")
        @Verify(Owner.class) String ownerKey) {
        Owner o = findOwner(ownerKey);
        String path = String.format("/owners/%s/atom", ownerKey);
        Feed feed = this.eventAdapter.toFeed(this.eventCurator.listMostRecent(FEED_LIMIT, o).list(), path);
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
    @ApiOperation(notes = "Retrieves a list of Events for an Owner", value = "Get Events")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found")})
    public List<Event> getEvents(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner o = findOwner(ownerKey);

        List<Event> events = this.eventCurator.listMostRecent(FEED_LIMIT, o).list();

        if (events != null) {
            eventAdapter.addMessageText(events);
        }

        return events;
    }

    private Owner findOwner(String key) {
        Owner owner = ownerCurator.lookupByKey(key);

        if (owner == null) {
            throw new NotFoundException(i18n.tr("owner with key: {0} was not found.", key));
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
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{owner_key}")
    @Transactional
    @ApiOperation(notes = "To un-set the defaultServiceLevel for an owner, submit an empty string.",
        value = "Update Owner")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public Owner updateOwner(@PathParam("owner_key") @Verify(Owner.class) String key,
        @ApiParam(name = "owner", required = true) Owner owner) {
        Owner toUpdate = findOwner(key);
        EventBuilder eventBuilder = eventFactory.getEventBuilder(Target.OWNER, Type.MODIFIED)
            .setOldEntity(toUpdate);

        log.debug("Updating owner: {}", key);

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

        // Update the autobindDisabled field if the incoming value is not null.
        if (owner.getAutobindDisabled() != null) {
            toUpdate.setAutobindDisabled(owner.getAutobindDisabled());
        }

        if (config.getBoolean(ConfigProperties.STANDALONE) &&
            toUpdate.getContentAccessMode() != null &&
            !toUpdate.getContentAccessMode().equals(owner.getContentAccessMode()) &&
            owner.getContentAccessMode() != null) {
            throw new BadRequestException(
                i18n.tr("The owner content access mode cannot be set directly in standalone mode."));
        }

        // Update the contentAccess field if the incoming value is not null.
        boolean refreshContentAccess = false;
        if (owner.getContentAccessMode() != null) {
            if (toUpdate.isAllowedContentAccessMode(owner.getContentAccessMode())) {
                String before = toUpdate.getContentAccessMode();
                String after = owner.getContentAccessMode();

                if (!after.equals(before)) {
                    toUpdate.setContentAccessMode(owner.getContentAccessMode());
                    refreshContentAccess = true;
                }
            }
            else {
                throw new BadRequestException(
                    i18n.tr("The content access mode is not allowed for this owner."));
            }
        }

        ownerCurator.merge(toUpdate);
        ownerCurator.flush();

        if (refreshContentAccess) {
            ownerManager.refreshOwnerForContentAccess(toUpdate);
        }

        Event e = eventBuilder.setNewEntity(toUpdate).buildEvent();
        sink.queueEvent(e);
        return toUpdate;
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
    @ApiOperation(notes = "Retrieves a list of Subscriptions for an Owner", value = "List Subscriptions")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public List<Subscription> getSubscriptions(@PathParam("owner_key") String ownerKey) {
        Owner owner = this.findOwner(ownerKey);

        List<Subscription> subscriptions = new LinkedList<Subscription>();

        for (Pool pool : this.poolManager.listPoolsByOwner(owner).list()) {
            SourceSubscription srcsub = pool.getSourceSubscription();

            if (srcsub != null && "master".equalsIgnoreCase(srcsub.getSubscriptionSubKey())) {
                subscriptions.add(this.poolManager.fabricateSubscriptionFromPool(pool));
            }
        }

        return subscriptions;
    }

    /**
     * Creates a Subscription for an Owner
     *
     * DEPRECATED: Please create pools directly with POST /pools.
     *
     * @deprecated Please create pools directly with POST /pools.
     * @return a Subscription object
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/subscriptions")
    @Deprecated
    @ApiOperation(notes = "Creates a Subscription for an Owner DEPRECATED: Please create " +
        "pools directly with POST /pools.", value = "Create Subscription")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public Subscription createSubscription(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        Subscription subscription) {

        // Correct owner & products
        Owner owner = findOwner(ownerKey);
        subscription.setOwner(owner);

        subscription = resolverUtil.resolveSubscription(subscription);

        if (subscription.getId() == null) {
            subscription.setId(Util.generateDbUUID());
        }

        poolManager.createAndEnrichPools(subscription);
        return subscription;
    }

    /**
     * Updates a Subscription for an Owner
     *
     * @deprecated Please update pools directly with POST /pools.
     * @httpcode 404
     * @httpcode 200
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/subscriptions")
    @Deprecated
    @ApiOperation(
        notes = "Updates a Subscription for an Owner.  Please " + "update pools directly with POST /pools.",
        value = "Update Subscription")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public void updateSubscription(
        @ApiParam(name = "subscription", required = true) Subscription subscription) {
        Pool existingPool = this.poolManager.getMasterPoolBySubscriptionId(subscription.getId());
        if (existingPool == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find a subscription with the ID ''{0}''.", subscription.getId()));
        }
        Pool updatedPool = this.poolManager.convertToMasterPool(subscription);
        updatedPool.setId(existingPool.getId());
        updatePool(subscription.getOwner().getKey(), updatedPool);

    }

    /**
     * Refreshes the Pools for an Owner
     * <p>
     * 'Tickle' an owner to have all of their entitlement pools synced with
     * their subscriptions. This method (and the one below may not be entirely
     * RESTful, as the updated data is not supplied as an argument.
     *
     * This API call is only relevant in a top level hosted deployment where subscriptions
     * and products are sourced from adapters. Calling this in an on-site deployment
     * is just a no-op.
     *
     * @param ownerKey unique id key of the owner whose pools should be updated
     * @return a JobDetail object
     * @httpcode 404
     * @httpcode 202
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("{owner_key}/subscriptions")
    @ApiOperation(notes = "Tickle an owner to have all of their entitlement pools synced with their " +
        "subscriptions. This method (and the one below may not be entirely RESTful, " +
        "as the updated data is not supplied as an argument. " +
        "This API call is only relevant in a top level hosted deployment where " +
        "subscriptions and products are sourced from adapters. Calling this in " +
        "an on-site deployment is just a no-op.", value = "Update Subscription")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found"),
        @ApiResponse(code = 202, message = "") })
    public JobDetail refreshPools(
        // TODO: Can we verify with autocreate?
        @PathParam("owner_key") String ownerKey,
        @QueryParam("auto_create_owner") @DefaultValue("false") Boolean autoCreateOwner,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        Owner owner = ownerCurator.lookupByKey(ownerKey);
        if (owner == null) {
            if (autoCreateOwner && ownerService.isOwnerKeyValidForCreation(ownerKey)) {
                owner = this.createOwner(new Owner(ownerKey, ownerKey));
            }
            else {
                throw new NotFoundException(i18n.tr("owner with key: {0} was not found.", ownerKey));
            }
        }

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        return RefreshPoolsJob.forOwner(owner, lazyRegen);
    }

    /**
     * Creates a custom pool for an Owner. Floating pools are not tied to any
     * upstream subscription, and are most commonly used for custom content
     * delivery in Satellite.
     * Also helps in on-site deployment testing
     *
     * @return a Pool object
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/pools")
    @ApiOperation(notes = "Creates a custom pool for an Owner. Floating pools are not tied to any " +
        "upstream subscription, and are most commonly used for custom content delivery " +
        "in Satellite. Also helps in on-site deployment testing", value = "Create Pool")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public Pool createPool(@PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @ApiParam(name = "pool", required = true) Pool pool) {

        log.info("Creating custom pool for owner {}: {}" + ownerKey, pool);

        // Correct owner & products
        Owner owner = findOwner(ownerKey);
        pool.setOwner(owner);

        pool = resolverUtil.resolvePool(pool);
        return poolManager.createAndEnrichPools(pool);
    }

    /**
     * Updates a pool for an Owner.
     * assumes this is a normal pool, and errors out otherwise cause we cannot
     * create master pools from bonus pools
     * TODO: while this method replaces the now deprecated updateSubsciption, it
     * still uses it underneath. We need to re-implement the wheel like we did
     * in createPool
     *
     * @httpcode 404
     * @httpcode 200
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/pools")
    @ApiOperation(
        notes = "Updates a pool for an Owner. assumes this is a normal pool, and " +
        "errors out otherwise cause we cannot create master pools from bonus pools " +
        "TODO: while this method replaces the now deprecated updateSubsciption, it " +
        "still uses it underneath. We need to re-implement the wheel like we did in " +
        "createPool ",
        value = "Update Pool")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public void updatePool(@PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @ApiParam(name = "pool", required = true) Pool newPool) {

        Pool currentPool = this.poolManager.find(newPool.getId());
        if (currentPool == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find a pool with the ID \"{0}\".", newPool.getId()));
        }

        if (currentPool.getType() != PoolType.NORMAL || newPool.getType() != PoolType.NORMAL) {
            throw new BadRequestException(i18n.tr("Cannot update bonus pools, as they are auto generated"));
        }

        /*
         * These are @JsonIgnored. If a client creates a pool and subsequently
         * wants to update it , we need to ensure Products are set
         * appropriately.
         */
        newPool.setProduct(currentPool.getProduct());
        newPool.setDerivedProduct(currentPool.getDerivedProduct());

        newPool = resolverUtil.resolvePool(newPool);

        this.poolManager.updateMasterPool(newPool);
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
    @ApiOperation(notes = "Removes Imports for an Owner. Cleans out all imported subscriptions " +
        "and triggers a background refresh pools. Link to an upstream distributor is " +
        "removed for the owner, so they can import from another distributor. Other " +
        "owners can also now import the manifests originally used in this owner. This  " +
        "call does not differentiate between any specific import, it just destroys all " +
        "subscriptions with an upstream pool ID, essentially anything from an import." +
        " Custom subscriptions will be left alone. Imports do carry rules and product " +
        "information which is global to the candlepin server. This import data is *not* " +
        "undone, we assume that updates to this data can be safely kept. ", value = "Undo Imports")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found")})
    public JobDetail undoImports(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey, @Context Principal principal) {

        Owner owner = findOwner(ownerKey);

        if (this.exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner) == null) {
            throw new NotFoundException("No import found for owner " + ownerKey);
        }

        return UndoImportsJob.forOwner(owner, false);
    }

    /**
     * Imports a manifest zip file for the given organization.
     *
     * This will bring in any products, content, and subscriptions that were assigned to
     * the distributor who generated the manifest.
     *
     * @deprecated use GET /owners/:owner_key/imports/async
     *
     * @return a ImportRecord object if the import is successful.
     * @httpcode 400
     * @httpcode 404
     * @httpcode 500
     * @httpcode 200
     * @httpcode 409
     */
    @POST
    @Path("{owner_key}/imports")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ApiOperation(
        notes = "Imports a manifest zip file for the given organization. " +
        "This will bring in any products, content, and subscriptions that were " +
        "assigned to the distributor who generated the manifest.", value = "Import Manifest")
    @ApiResponses({ @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "Owner not found"), @ApiResponse(code = 500, message = ""),
        @ApiResponse(code = 409, message = "") })
    @Deprecated
    public ImportRecord importManifest(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("force") String[] overrideConflicts,
        MultipartInput input) {

        ConflictOverrides overrides = processConflictOverrideParams(overrideConflicts);
        UploadMetadata fileData = new UploadMetadata();
        Owner owner = findOwner(ownerKey);
        try {
            fileData = getArchiveFromResponse(input);
            return manifestManager.importManifest(owner, fileData.getData(), fileData.getUploadedFilename(),
                overrides);
        }
        catch (IOException e) {
            log.error("Reading error during importing", e);
            manifestManager.recordImportFailure(owner, e, fileData.getUploadedFilename());
            throw new IseException(i18n.tr("Error reading export archive"), e);
        }
        // These come back with internationalized messages, so we can transfer:
        catch (SyncDataFormatException e) {
            log.error("Format error of the data in a manifest", e);
            manifestManager.recordImportFailure(owner, e, fileData.getUploadedFilename());
            throw new BadRequestException(e.getMessage(), e);
        }
        catch (ImporterException e) {
            log.error("Problem with archive", e);
            manifestManager.recordImportFailure(owner, e, fileData.getUploadedFilename());
            throw new IseException(e.getMessage(), e);
        }
        // Grab candlepin exceptions to record the error and then rethrow
        // to pass on the http return code
        catch (CandlepinException e) {
            log.error("Recording import failure", e);
            manifestManager.recordImportFailure(owner, e, fileData.getUploadedFilename());
            throw e;
        }
        finally {
            log.info("Import attempt completed for owner {}", owner.getDisplayName());
        }
    }

    /**
     * Initiates an asynchronous manifest import for the given organization. The details of
     * the started job can be obtained via the {@link JobResource}.
     *
     * This will bring in any products, content, and subscriptions that were assigned to
     * the distributor who generated the manifest.
     *
     * @return a JobDetail object representing the newly started {@link ImportJob}.
     * @httpcode 400
     * @httpcode 404
     * @httpcode 500
     * @httpcode 200
     * @httpcode 409
     */
    @POST
    @Path("{owner_key}/imports/async")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ApiOperation(
        notes = "Initiates an asynchronous manifest import for the given organization. " +
        "This will bring in any products, content, and subscriptions that were " +
        "assigned to the distributor who generated the manifest.",
        value = "Import Manifest Asynchronously")
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "Owner not found"),
        @ApiResponse(code = 500, message = ""),
        @ApiResponse(code = 409, message = "")})
    public JobDetail importManifestAsync(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("force") String[] overrideConflicts,
        MultipartInput input) {

        ConflictOverrides overrides = processConflictOverrideParams(overrideConflicts);

        UploadMetadata fileData = new UploadMetadata();
        Owner owner = findOwner(ownerKey);
        try {
            fileData = getArchiveFromResponse(input);
            String archivePath = fileData.getData().getAbsolutePath();
            log.info("Running async import of archive {} for owner {}", archivePath, owner.getDisplayName());
            return manifestManager.importManifestAsync(owner, fileData.getData(),
                fileData.getUploadedFilename(), overrides);
        }
        catch (IOException e) {
            manifestManager.recordImportFailure(owner, e, fileData.getUploadedFilename());
            throw new IseException(i18n.tr("Error reading export archive"), e);
        }
        catch (ManifestFileServiceException e) {
            manifestManager.recordImportFailure(owner, e, fileData.getUploadedFilename());
            throw new IseException(i18n.tr("Error storing uploaded archive for asynchronous processing."), e);
        }
        catch (CandlepinException e) {
            manifestManager.recordImportFailure(owner, e, fileData.getUploadedFilename());
            throw e;
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
    @ApiOperation(notes = " Retrieves a list of Import Records for an Owner", value = "Get Imports",
        response = ImportRecord.class, responseContainer = "list")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public CandlepinQuery<ImportRecord> getImports(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwner(ownerKey);

        return this.importRecordCurator.findRecords(owner);
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
    @ApiOperation(notes = "Creates an Ueber Entitlement Certificate. If a certificate " +
        "already exists, it will be regenerated.",
        value = "Create Ueber Entitlement Certificate")
    @ApiResponses({
        @ApiResponse(code = 404, message = "Owner not found"),
        @ApiResponse(code = 400, message = "")
        }
    )
    public UeberCertificate createUeberCertificate(@Context Principal principal,
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey) {
        return ueberCertGenerator.generate(ownerKey, principal);
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
    @ApiOperation(notes = "Retrieves the Ueber Entitlement Certificate",
        value = "Get Ueber Entitlement Certificate")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public UeberCertificate getUeberCertificate(@Context Principal principal,
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey) {

        Owner o = findOwner(ownerKey);
        if (o == null) {
            throw new NotFoundException(i18n.tr("Owner with key: {0} was not found.", ownerKey));
        }

        UeberCertificate ueberCert = ueberCertCurator.findForOwner(o);
        if (ueberCert == null) {
            throw new NotFoundException(
                i18n.tr("uber certificate for owner {0} was not found. Please generate one.",
                    o.getKey()));
        }
        return ueberCert;
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
    @ApiOperation(notes = " Retrieves a list of Upstream Consumers for an Owner",
        value = "Get Upstream Consumers")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public List<UpstreamConsumer> getUpstreamConsumers(@Context Principal principal,
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey) {
        Owner o = findOwner(ownerKey);
        if (o == null) {
            throw new NotFoundException(i18n.tr("owner with key: {0} was not found.", ownerKey));
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
    @ApiOperation(notes = "Retrieves a list of Hypervisors for an Owner", value = "Get Hypervisors",
        response = Consumer.class, responseContainer = "list")
    @ApiResponses({ @ApiResponse(code = 404, message = "Owner not found") })
    public CandlepinQuery<Consumer> getHypervisors(
        @PathParam("owner_key") @Verify(Owner.class) String ownerKey,
        @QueryParam("hypervisor_id") List<String> hypervisorIds) {

        CandlepinQuery<Consumer> out = (hypervisorIds == null || hypervisorIds.isEmpty()) ?
            this.consumerCurator.getHypervisorsForOwner(ownerKey) :
            this.consumerCurator.getHypervisorsBulk(hypervisorIds, ownerKey);

        return out;
    }

    private ConflictOverrides processConflictOverrideParams(String[] overrideConflicts) {
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

        return overrides;
    }

    private UploadMetadata getArchiveFromResponse(MultipartInput input) throws IOException {
        String filename = "";
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
        return new UploadMetadata(part.getBody(new GenericType<File>() {}), filename);
    }

    /**
     * A private class that stores data related to a file upload request.
     */
    private class UploadMetadata {
        private File data;
        private String uploadedFilename;

        public UploadMetadata(File data, String uploadedFilename) {
            this.data = data;
            this.uploadedFilename = uploadedFilename;
        }

        public UploadMetadata() {
            this.data = null;
            this.uploadedFilename = "";
        }

        public File getData() {
            return data;
        }

        public String getUploadedFilename() {
            return uploadedFilename;
        }

    }
}
