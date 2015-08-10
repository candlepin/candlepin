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
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.CandlepinException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.common.paging.Paginate;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerialDto;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.DeleteResult;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.EventCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Release;
import org.candlepin.model.User;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.pinsetter.tasks.EntitleByProductsJob;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerInstalledProductEnricher;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.resteasy.parameter.CandlepinParam;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.sync.ExportCreationException;
import org.candlepin.sync.Exporter;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * API Gateway for Consumers
 */
@Path("/consumers")
public class ConsumerResource {
    private Pattern consumerSystemNamePattern;
    private Pattern consumerPersonNamePattern;

    private static Logger log = LoggerFactory.getLogger(ConsumerResource.class);
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductCurator productCurator;
    private SubscriptionServiceAdapter subAdapter;
    private EntitlementCurator entitlementCurator;
    private IdentityCertServiceAdapter identityCertService;
    private EntitlementCertServiceAdapter entCertService;
    private UserServiceAdapter userService;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private EventCurator eventCurator;
    private EventAdapter eventAdapter;
    private static final int FEED_LIMIT = 1000;
    private Exporter exporter;
    private PoolManager poolManager;
    private ConsumerRules consumerRules;
    private OwnerCurator ownerCurator;
    private ActivationKeyCurator activationKeyCurator;
    private Entitler entitler;
    private ComplianceRules complianceRules;
    private DeletedConsumerCurator deletedConsumerCurator;
    private EnvironmentCurator environmentCurator;
    private DistributorVersionCurator distributorVersionCurator;
    private CdnCurator cdnCurator;
    private Configuration config;
    private CalculatedAttributesUtil calculatedAttributesUtil;
    private ConsumerBindUtil consumerBindUtil;

    @Inject
    public ConsumerResource(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        ProductCurator productCurator,
        SubscriptionServiceAdapter subAdapter,
        EntitlementCurator entitlementCurator,
        IdentityCertServiceAdapter identityCertService,
        EntitlementCertServiceAdapter entCertServiceAdapter, I18n i18n,
        EventSink sink, EventFactory eventFactory, EventCurator eventCurator,
        EventAdapter eventAdapter, UserServiceAdapter userService,
        Exporter exporter, PoolManager poolManager,
        ConsumerRules consumerRules, OwnerCurator ownerCurator,
        ActivationKeyCurator activationKeyCurator, Entitler entitler,
        ComplianceRules complianceRules, DeletedConsumerCurator deletedConsumerCurator,
        EnvironmentCurator environmentCurator,
        DistributorVersionCurator distributorVersionCurator,
        Configuration config, ContentCurator contentCurator,
        CdnCurator cdnCurator, CalculatedAttributesUtil calculatedAttributesUtil,
        ConsumerBindUtil consumerBindUtil) {

        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productCurator = productCurator;
        this.subAdapter = subAdapter;
        this.entitlementCurator = entitlementCurator;
        this.identityCertService = identityCertService;
        this.entCertService = entCertServiceAdapter;
        this.i18n = i18n;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.eventCurator = eventCurator;
        this.userService = userService;
        this.exporter = exporter;
        this.poolManager = poolManager;
        this.consumerRules = consumerRules;
        this.ownerCurator = ownerCurator;
        this.eventAdapter = eventAdapter;
        this.activationKeyCurator = activationKeyCurator;
        this.entitler = entitler;
        this.complianceRules = complianceRules;
        this.deletedConsumerCurator = deletedConsumerCurator;
        this.environmentCurator = environmentCurator;
        this.distributorVersionCurator = distributorVersionCurator;
        this.cdnCurator = cdnCurator;
        this.consumerPersonNamePattern = Pattern.compile(config.getString(
            ConfigProperties.CONSUMER_PERSON_NAME_PATTERN));
        this.consumerSystemNamePattern = Pattern.compile(config.getString(
            ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN));
        this.config = config;
        this.calculatedAttributesUtil = calculatedAttributesUtil;
        this.consumerBindUtil = consumerBindUtil;
    }

    /**
     * Retrieves a list of the Consumers
     *
     * @return list of Consumer objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "consumers")
    @Paginate
    public List<Consumer> list(@QueryParam("username") String userName,
        @QueryParam("type") Set<String> typeLabels,
        @QueryParam("owner") String ownerKey,
        @QueryParam("uuid") List<String> uuids,
        @QueryParam("hypervisor_id") List<String> hypervisorIds,
        @QueryParam("fact") @CandlepinParam(type = KeyValueParameter.class)
            List<KeyValueParameter> attrFilters,
        @Context PageRequest pageRequest) {

        Owner owner = null;
        if (ownerKey != null) {
            owner = ownerCurator.lookupByKey(ownerKey);
            if (owner == null) {
                throw new NotFoundException(i18n.tr(
                    "owner with key: {0} was not found.", ownerKey));
            }
        }

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
     * Checks for the existence of a Consumer
     * <p>
     * This method is used to check if a consumer is available on a particular
     * shard.  There is no need to do a full GET for the consumer for this check.
     *
     * @param uuid uuid of the consumer sought.
     * @httpcode 404 If the consumer doesn't exist or cannot be accessed
     * @httpcode 204 If the consumer exists and can be accessed
     */
    @HEAD
    @Path("{consumer_uuid}/exists")
    public void consumerExists(
        @PathParam("consumer_uuid") String uuid) {
        if (!consumerCurator.doesConsumerExist(uuid)) {
            throw new NotFoundException(i18n.tr(
                "Consumer with id {0} could not be found.", uuid));
        }
    }

    /**
     * Retrieves a single Consumer
     * <p>
     * <pre>
     * {
     *   "id" : "database_id",
     *   "uuid" : "consumer_id",
     *   "name" : "client.rdu.redhat.com",
     *   "username" : "admin",
     *   "entitlementStatus" : "invalid",
     *   "serviceLevel" : "",
     *   "releaseVer" : {},
     *   "type" : {
     *     "id" : "database_id",
     *     "label" : "system",
     *     "manifest" : false
     *   },
     *   "owner" : {},
     *   "environment" : null,
     *   "entitlementCount" : 1,
     *   "lastCheckin" : "",
     *   "installedProducts" : [],
     *   "canActivate" : false,
     *   "guestIds" : [ ],
     *   "capabilities" : [ ],
     *   "hypervisorId" : null,
     *   "autoheal" : true,
     *   "href" : "/consumers/consumer_id",
     *   "created" : [date],
     *   "updated" : [date]
     * }
     * </pre>
     *
     * @param uuid uuid of the consumer sought
     * @return a Consumer object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}")
    public Consumer getConsumer(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);

        if (consumer != null) {
            IdentityCertificate idcert = consumer.getIdCert();
            if (idcert != null) {
                Date expire = idcert.getSerial().getExpiration();
                int days = config.getInt(
                        ConfigProperties.IDENTITY_CERT_EXPIRY_THRESHOLD, 90);
                Date futureExpire = Util.addDaysToDt(days);
                // if expiration is within 90 days, regenerate it
                log.debug("Threshold [{}] expires on [{}] futureExpire [{}]",
                        days, expire, futureExpire);

                if (expire.before(futureExpire)) {
                    log.info(
                            "Regenerating identity certificate for consumer: {}, expiry: {}",
                            uuid, expire);
                    consumer = this.regenerateIdentityCertificates(uuid);
                }
            }

            // enrich with subscription data
            consumer.setCanActivate(subAdapter.canActivateSubscription(consumer));
            // enrich with installed product data
            addDataToInstalledProducts(consumer);
        }

        return consumer;
    }

    /**
     * Creates a Consumer
     * <p>
     * NOTE: Opening this method up to everyone, as we have
     * nothing we can reliably verify in the method signature. Instead we have
     * to figure out what owner this consumer is destined for (due to backward
     * compatability with existing clients which do not specify an owner during
     * registration), and then check the access to the specified owner in the
     * method itself.
     *
     * @param consumer Consumer metadata
     * @return a Consumer object
     * @throws BadRequestException generic exception type for web services We
     *         are calling this "registerConsumer" in the api discussions
     * @httpcode 400
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole(noAuth = true)
    @Transactional
    public Consumer create(Consumer consumer, @Context Principal principal,
        @QueryParam("username") String userName,
        @QueryParam("owner") String ownerKey,
        @QueryParam("activation_keys") String activationKeys,
        @QueryParam("identity_cert_creation") @DefaultValue("true") boolean identityCertCreation)
        throws BadRequestException {
        // API:registerConsumer
        Set<String> keyStrings = splitKeys(activationKeys);

        // Only let NoAuth principals through if there are activation keys to consider:
        if ((principal instanceof NoAuthPrincipal) && keyStrings.isEmpty()) {
            throw new ForbiddenException(i18n.tr("Insufficient permissions"));
        }

        if (!keyStrings.isEmpty()) {
            if (ownerKey == null) {
                throw new BadRequestException(i18n.tr(
                        "Must specify an org to register with activation keys."));
            }
            if (userName != null) {
                throw new BadRequestException(i18n.tr("Cannot specify username with activation keys."));
            }
        }

        Owner owner = setupOwner(principal, ownerKey);
        // Raise an exception if none of the keys specified exist for this owner.
        List<ActivationKey> keys = checkActivationKeys(principal, owner, keyStrings);

        userName = setUserName(consumer, principal, userName);

        checkConsumerName(consumer);

        ConsumerType type = lookupConsumerType(consumer.getType().getLabel());
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            if (keys.size() > 0) {
                throw new BadRequestException(i18n.tr(
                        "A unit type of ''person'' cannot be used with activation keys"));
            }
            if (!isConsumerPersonNameValid(consumer.getName())) {
                throw new BadRequestException(i18n.tr(
                        "System name cannot contain most special characters."));
            }

            verifyPersonConsumer(consumer, type, owner, userName, principal);
        }

        if (type.isType(ConsumerTypeEnum.SYSTEM) &&
            !isConsumerSystemNameValid(consumer.getName())) {

            throw new BadRequestException(i18n.tr("System name cannot contain most special characters."));
        }
        consumer.setOwner(owner);
        consumer.setType(type);
        consumer.setCanActivate(subAdapter.canActivateSubscription(consumer));
        consumer.setAutoheal(true); // this is the default
        if (consumer.getServiceLevel() == null) { consumer.setServiceLevel(""); }

        // If no service level was specified, and the owner has a default set, use it:
        if (consumer.getServiceLevel().equals("") &&
            owner.getDefaultServiceLevel() != null) {
            consumer.setServiceLevel(owner.getDefaultServiceLevel());
        }
        updateCapabilities(consumer, null);

        logNewConsumerDebugInfo(consumer, keys, type);

        if (consumer.getInstalledProducts() != null) {
            for (ConsumerInstalledProduct p : consumer.getInstalledProducts()) {
                p.setConsumer(consumer);
            }
        }
        if (consumer.getGuestIds() != null) {
            for (GuestId g : consumer.getGuestIds()) {
                g.setConsumer(consumer);
            }
            consumer.addGuestIdCheckIn();
        }

        HypervisorId hvsrId = consumer.getHypervisorId();
        if (hvsrId != null && hvsrId.getHypervisorId() != null && !hvsrId.getHypervisorId().isEmpty()) {
            // If a hypervisorId is supplied, make sure the consumer and owner are correct
            hvsrId.setConsumer(consumer);
        }

        consumerBindUtil.validateServiceLevel(owner, consumer.getServiceLevel());

        try {
            Date createdDate = consumer.getCreated();
            Date lastCheckIn = consumer.getLastCheckin();
            // create sets created to current time.
            consumer = consumerCurator.create(consumer);
            //  If we sent in a created date, we want it persisted at the update below
            if (createdDate != null) {
                consumer.setCreated(createdDate);
            }
            if (lastCheckIn != null) {
                log.info("Creating with specific last checkin time: {}",
                        consumer.getLastCheckin());
                consumer.addCheckIn(lastCheckIn);
            }
            if (identityCertCreation) {
                IdentityCertificate idCert = generateIdCert(consumer, false);
                consumer.setIdCert(idCert);
            }

            sink.emitConsumerCreated(consumer);

            if (keys.size() > 0) {
                consumerBindUtil.handleActivationKeys(consumer, keys);
            }

            // Don't allow complianceRules to update entitlementStatus, because we're about to perform
            // an update unconditionally.
            complianceRules.getStatus(consumer, null, false, false);
            consumerCurator.update(consumer);

            log.info("Consumer " + consumer.getUuid() + " created in org " + consumer.getOwner().getKey());

            return consumer;
        }
        catch (CandlepinException ce) {
            // If it is one of ours, rethrow it.
            throw ce;
        }
        catch (Exception e) {
            log.error("Problem creating unit:", e);
            throw new BadRequestException(i18n.tr(
                "Problem creating unit {0}", consumer));
        }
    }

    private List<ActivationKey>  checkActivationKeys(Principal principal, Owner owner,
            Set<String> keyStrings) throws BadRequestException {
        List<ActivationKey> keys = new ArrayList<ActivationKey>();
        for (String keyString : keyStrings) {
            ActivationKey key = null;
            try {
                key = findKey(keyString, owner);
                keys.add(key);
            }
            catch (NotFoundException e) {
                log.warn(e.getMessage());
            }
        }
        if ((principal instanceof NoAuthPrincipal) && keys.isEmpty()) {
            throw new BadRequestException(i18n.tr(
                    "None of the activation keys specified exist for this org."));
        }
        return keys;
    }

    /**
     * @param consumer
     * @param principal
     * @param userName
     * @return a String object
     */
    private String setUserName(Consumer consumer, Principal principal,
        String userName) {
        if (userName == null) {
            userName = principal.getUsername();
        }

        if (userName != null) {
            consumer.setUsername(userName);
        }
        return userName;
    }

    /**
     * @param existing
     * @param update
     * @return a String object
     */
    private boolean updateCapabilities(Consumer existing, Consumer update) {
        boolean change = false;
        if (update == null) {
            // create
            if ((existing.getCapabilities() == null ||
                existing.getCapabilities().isEmpty()) &&
                existing.getFact("distributor_version") !=  null) {
                Set<DistributorVersionCapability> capabilities = distributorVersionCurator.
                    findCapabilitiesByDistVersion(existing.getFact("distributor_version"));
                if (capabilities != null) {
                    Set<ConsumerCapability> ccaps = new HashSet<ConsumerCapability>();
                    for (DistributorVersionCapability dvc : capabilities) {
                        ConsumerCapability cc =
                            new ConsumerCapability(existing, dvc.getName());
                        ccaps.add(cc);
                    }
                    existing.setCapabilities(ccaps);
                }
                change = true;
            }
        }
        else {
            // update
            if (update.getCapabilities() != null) {
                change = update.getCapabilities().equals(existing.getCapabilities());
                existing.setCapabilities(update.getCapabilities());
            }
            else if (update.getFact("distributor_version") !=  null) {
                DistributorVersion dv = distributorVersionCurator.findByName(
                    update.getFact("distributor_version"));
                if (dv != null) {
                    Set<ConsumerCapability> ccaps = new HashSet<ConsumerCapability>();
                    for (DistributorVersionCapability dvc : dv.getCapabilities()) {
                        ConsumerCapability cc =
                            new ConsumerCapability(existing, dvc.getName());
                        ccaps.add(cc);
                    }
                    existing.setCapabilities(ccaps);
                }
                change = true;
            }
        }
        if (change) {
            log.info("Capabilities changed.");
        }
        return change;
    }

    /**
     * @param consumer
     * @param principal
     * @param userName
     * @return a String object
     */
    private void checkConsumerName(Consumer consumer) {
        // for now this applies to both types consumer
        if (consumer.getName() != null &&
            consumer.getName().indexOf('#') == 0) {
            // this is a bouncycastle restriction
            throw new BadRequestException(
                i18n.tr("System name cannot begin with # character"));
        }
    }

    private void logNewConsumerDebugInfo(Consumer consumer,
        List<ActivationKey> keys, ConsumerType type) {
        if (log.isDebugEnabled()) {
            log.debug("Got consumerTypeLabel of: {}", type.getLabel());
            if (consumer.getFacts() != null) {
                log.debug("incoming facts:");
                for (String key : consumer.getFacts().keySet()) {
                    log.debug("   {} = {}", key, consumer.getFact(key));
                }
            }

            log.debug("Activation keys:");
            for (ActivationKey activationKey : keys) {
                log.debug("   {}", activationKey.getName());
            }
        }
    }

    private ActivationKey findKey(String keyName, Owner owner) {
        ActivationKey key = activationKeyCurator.lookupForOwner(keyName, owner);

        if (key == null) {
            throw new NotFoundException(i18n.tr(
                "Activation key ''{0}'' not found for organization ''{1}''.",
                keyName, owner.getKey()));
        }
        return key;
    }

    private void verifyPersonConsumer(Consumer consumer, ConsumerType type,
        Owner owner, String username, Principal principal) {

        User user = null;
        try {
            user = userService.findByLogin(username);
        }
        catch (UnsupportedOperationException e) {
            log.warn("User service does not allow user lookups, " +
                "cannot verify person consumer.");
        }

        if (user == null) {
            throw new NotFoundException(
                i18n.tr("User with ID ''{0}'' could not be found."));
        }

        // When registering person consumers we need to be sure the username
        // has some association with the owner the consumer is destined for:
        if (!principal.canAccess(owner, SubResource.NONE, Access.ALL) &&
            !principal.hasFullAccess()) {
            throw new ForbiddenException(i18n.tr(
                "User ''{0}'' has no roles for organization ''{1}''",
                user.getUsername(), owner.getKey()));
        }

        // TODO: Refactor out type specific checks?
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            Consumer existing = consumerCurator.findByUser(user);

            if (existing != null &&
                existing.getType().isType(ConsumerTypeEnum.PERSON)) {
                // TODO: This is not the correct error code for this situation!
                throw new BadRequestException(i18n.tr(
                    "User ''{0}'' has already registered a personal consumer",
                    user.getUsername()));
            }
            consumer.setName(user.getUsername());
        }
    }

    private Owner setupOwner(Principal principal, String ownerKey) {
        // If no owner was specified, try to assume based on which owners the
        // principal
        // has admin rights for. If more than one, we have to error out.
        if (ownerKey == null && (principal instanceof UserPrincipal)) {
            // check for this cast?
            List<String> ownerKeys = ((UserPrincipal) principal).getOwnerKeys();

            if (ownerKeys.size() != 1) {
                throw new BadRequestException(
                    i18n.tr("You must specify an organization for new units."));
            }

            ownerKey = ownerKeys.get(0);
        }

        createOwnerIfNeeded(principal);

        Owner owner = ownerCurator.lookupByKey(ownerKey);
        if (owner == null) {
            throw new BadRequestException(i18n.tr(
                "Organization {0} does not exist.", ownerKey));
        }

        // Check permissions for current principal on the owner:
        if ((principal instanceof UserPrincipal) &&
            !principal.canAccess(owner, SubResource.CONSUMERS, Access.CREATE)) {
            log.warn("User {} does not have access to create consumers in org {}",
                principal.getPrincipalName(), owner.getKey());
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", owner.getKey()));
        }

        return owner;
    }

    /*
     * verify that the consumer name is approriate for system
     * consumers
     */
    private boolean isConsumerSystemNameValid(String name) {
        if (name == null) {
            return false;
        }

        return consumerSystemNamePattern.matcher(name).matches();
    }

    /*
     * verify the consumer name is approariate for person consumers
     */
    private boolean isConsumerPersonNameValid(String name) {
        if (name == null) {
            return false;
        }

        return consumerPersonNamePattern.matcher(name).matches();
    }

    /*
     * During registration of new consumers we support an edge case where the
     * user service may have authenticated a username/password for an owner
     * which we have not yet created in the Candlepin database. If we detect
     * this during registration we need to create the new owner, and adjust the
     * principal that was created during authentication to carry it.
     */
    // TODO: Re-evaluate if this is still an issue with the new membership
    // scheme!
    private void createOwnerIfNeeded(Principal principal) {
        if (!(principal instanceof UserPrincipal)) {
            // If this isn't a user principal we can't check for owners that may
            // need to
            // be created.
            return;
        }
        for (Owner owner : ((UserPrincipal) principal).getOwners()) {
            Owner existingOwner = ownerCurator.lookupByKey(owner.getKey());
            if (existingOwner == null) {
                log.info("Principal carries permission for owner that does not exist.");
                log.info("Creating new owner: {}", owner.getKey());
                existingOwner = ownerCurator.create(owner);
                poolManager.getRefresher(subAdapter).add(existingOwner).run();
            }
        }
    }

    private ConsumerType lookupConsumerType(String label) {
        ConsumerType type = consumerTypeCurator.lookupByLabel(label);

        if (type == null) {
            throw new BadRequestException(i18n.tr(
                "Unit type ''{0}'' could not be found.", label));
        }
        return type;
    }

    /**
     * Updates a Consumer
     *
     * @httpcode 404
     * @httpcode 200
     */
    // While this is a PUT, we are treating it as a PATCH until this operation
    // becomes more prevalent. We only update the portions of the consumer that appear
    // to be set.
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}")
    @Transactional
    public void updateConsumer(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid,
        Consumer consumer) {
        Consumer toUpdate = consumerCurator.verifyAndLookupConsumer(uuid);

        VirtConsumerMap guestConsumerMap = new VirtConsumerMap();
        VirtConsumerMap guestsHostConsumerMap = new VirtConsumerMap();
        if (consumer.getGuestIds() != null) {
            Set<String> allGuestIds = new HashSet<String>();
            for (GuestId gid : consumer.getGuestIds()) {
                allGuestIds.add(gid.getGuestId());
            }
            guestConsumerMap = consumerCurator.getGuestConsumersMap(
                    toUpdate.getOwner(), allGuestIds);

            guestsHostConsumerMap = consumerCurator.getGuestsHostMap(
                    toUpdate.getOwner(), allGuestIds);
        }

        if (performConsumerUpdates(consumer, toUpdate,
                guestConsumerMap, guestsHostConsumerMap)) {
            try {
                consumerCurator.update(toUpdate);
            }
            catch (CandlepinException ce) {
                // If it is one of ours, rethrow it.
                throw ce;
            }
            catch (Exception e) {
                log.error("Problem updating unit:", e);
                throw new BadRequestException(i18n.tr(
                    "Problem updating unit {0}", consumer));
            }
        }
    }

    public boolean performConsumerUpdates(Consumer updated, Consumer toUpdate,
            VirtConsumerMap guestConsumerMap,
            VirtConsumerMap guestHypervisorConsumers) {
        return performConsumerUpdates(updated, toUpdate, guestConsumerMap, guestHypervisorConsumers, true);
    }

    @Transactional
    public boolean performConsumerUpdates(Consumer updated, Consumer toUpdate,
            VirtConsumerMap guestConsumerMap,
            VirtConsumerMap guestHypervisorConsumers,
            boolean isIdCert) {
        if (log.isDebugEnabled()) {
            log.debug("Updating consumer: {}", toUpdate.getUuid());
        }

        // We need a representation of the consumer before making any modifications.
        // If nothing changes we won't send.  The new entity needs to be correct though,
        // so we should get a Jsonstring now, and finish it off if we're going to send
        EventBuilder eventBuilder = eventFactory.getEventBuilder(Target.CONSUMER, Type.MODIFIED)
                .setOldEntity(toUpdate);

        // version changed on non-checked in consumer, or list of capabilities
        // changed on checked in consumer
        boolean changesMade = updateCapabilities(toUpdate, updated);

        changesMade = checkForFactsUpdate(toUpdate, updated) || changesMade;
        changesMade = checkForInstalledProductsUpdate(toUpdate, updated) || changesMade;
        changesMade = checkForGuestsUpdate(toUpdate, updated,
                guestConsumerMap, guestHypervisorConsumers) || changesMade;
        changesMade = checkForHypervisorIdUpdate(toUpdate, updated) || changesMade;

        if (updated.getContentTags() != null &&
            !updated.getContentTags().equals(toUpdate.getContentTags())) {
            log.info("   Updating content tags.");
            toUpdate.setContentTags(updated.getContentTags());
            changesMade = true;
        }

        // Allow optional setting of the autoheal attribute:
        if (updated.isAutoheal() != null &&
             !updated.isAutoheal().equals(toUpdate.isAutoheal())) {
            log.info("   Updating consumer autoheal setting.");
            toUpdate.setAutoheal(updated.isAutoheal());
            changesMade = true;
        }

        if (updated.getReleaseVer() != null &&
            (updated.getReleaseVer().getReleaseVer() != null) &&
            !updated.getReleaseVer().equals(toUpdate.getReleaseVer())) {
            log.info("   Updating consumer releaseVer setting.");
            toUpdate.setReleaseVer(updated.getReleaseVer());
            changesMade = true;
        }

        // Allow optional setting of the service level attribute:
        String level = updated.getServiceLevel();
        if (level != null &&
            !level.equals(toUpdate.getServiceLevel())) {
            log.info("   Updating consumer service level setting.");
            consumerBindUtil.validateServiceLevel(toUpdate.getOwner(), level);
            toUpdate.setServiceLevel(level);
            changesMade = true;
        }

        String environmentId =
            updated.getEnvironment() == null ? null : updated.getEnvironment().getId();
        if (environmentId != null && (toUpdate.getEnvironment() == null ||
                    !toUpdate.getEnvironment().getId().equals(environmentId))) {
            Environment e = environmentCurator.find(environmentId);
            if (e == null) {
                throw new NotFoundException(i18n.tr(
                    "Environment with ID ''{0}'' could not be found.", environmentId));
            }
            log.info("Updating environment to: " + environmentId);
            toUpdate.setEnvironment(e);

            // lazily regenerate certs, so the client can still work
            poolManager.regenerateEntitlementCertificates(toUpdate, true);
            changesMade = true;
        }

        // like the other values in an update, if consumer name is null, act as if
        // it should remain the same
        if (updated.getName() != null && !toUpdate.getName().equals(updated.getName())) {
            checkConsumerName(updated);

            log.info("Updating consumer name: {} -> {}",
                    toUpdate.getName(), updated.getName());
            toUpdate.setName(updated.getName());

            // get the new name into the id cert if we are using the cert
            if (isIdCert) {
                IdentityCertificate ic = generateIdCert(toUpdate, true);
                toUpdate.setIdCert(ic);
            }
        }

        if (updated.getLastCheckin() != null) {
            log.info("Updating to specific last checkin time: {}",
                    updated.getLastCheckin());
            toUpdate.addCheckIn(updated.getLastCheckin());
            changesMade = true;
        }

        if (changesMade) {
            log.debug("Consumer {} updated.", toUpdate.getUuid());

            // Set the updated date here b/c @PreUpdate will not get fired
            // since only the facts table will receive the update.
            toUpdate.setUpdated(new Date());

            // this should update compliance on toUpdate, but not call the curator
            complianceRules.getStatus(toUpdate, null, false, false);

            Event event = eventBuilder.setNewEntity(toUpdate).buildEvent();
            sink.queueEvent(event);
        }
        return changesMade;
    }

    private boolean checkForHypervisorIdUpdate(Consumer existing, Consumer incoming) {
        HypervisorId incomingId = incoming.getHypervisorId();
        if (incomingId != null) {
            HypervisorId existingId = existing.getHypervisorId();
            if (incomingId.getHypervisorId() == null ||
                    incomingId.getHypervisorId().isEmpty()) {
                // Allow hypervisorId to be removed
                existing.setHypervisorId(null);
            }
            else {
                if (existingId != null) {
                    if (existingId.getHypervisorId() != null &&
                        !existingId.getHypervisorId().equals(incomingId.getHypervisorId())) {
                        existingId.setHypervisorId(incomingId.getHypervisorId());
                    }
                    else {
                        return false;
                    }
                }
                else {
                    // Safer to build a new clean HypervisorId object
                    existing.setHypervisorId(
                        new HypervisorId(incomingId.getHypervisorId()));
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Check if the consumers facts have changed. If they do not appear to have been
     * specified in this PUT, skip updating facts entirely. It returns true if facts
     * were included in request and have changed
     *
     * @param existing existing consumer
     * @param incoming incoming consumer
     * @return a boolean
     */
    private boolean checkForFactsUpdate(Consumer existing, Consumer incoming) {
        if (incoming.getFacts() == null) {
            log.debug("Facts not included in this consumer update, skipping update.");
            return false;
        }
        else if (!existing.factsAreEqual(incoming)) {
            log.info("Updating facts.");
            existing.setFacts(incoming.getFacts());
            return true;
        }
        return false;
    }

    /**
     * Check if the consumers installed products have changed. If they do not appear to
     * have been specified in this PUT, skip updating installed products entirely.
     * <p>
     * It will return true if installed products were included in request and have changed.
     *
     * @param existing existing consumer
     * @param incoming incoming consumer
     * @return a boolean
     */
    private boolean checkForInstalledProductsUpdate(Consumer existing, Consumer incoming) {

        if (incoming.getInstalledProducts() == null) {
            log.debug("Installed packages not included in this consumer update, skipping update.");
            return false;
        }
        else if (!existing.getInstalledProducts().equals(incoming.getInstalledProducts())) {
            log.info("Updating installed products.");
            existing.getInstalledProducts().clear();
            for (ConsumerInstalledProduct cip : incoming.getInstalledProducts()) {
                existing.addInstalledProduct(cip);
            }
            return true;
        }
        log.debug("No change to installed products.");
        return false;
    }

    /**
     * Check if the consumers guest IDs have changed. If they do not appear to
     * have been specified in this PUT, skip updating guest IDs entirely.
     *
     * If a consumer's guest was already reported by another consumer (host),
     * all entitlements related to the other host are revoked. Also, if a
     * guest ID is removed from this host, then all entitlements related to
     * this host are revoked from the guest.
     * Will return true if guest IDs were included in request and have changed.
     *
     * @param existing existing consumer
     * @param incoming incoming consumer
     * @return a boolean
     */
    private boolean checkForGuestsUpdate(Consumer existing, Consumer incoming,
            VirtConsumerMap guestConsumerMap,
            VirtConsumerMap guestHypervisorConsumers) {

        if (incoming.getGuestIds() == null) {
            log.debug("Guests not included in this consumer update, skipping update.");
            return false;
        }

        log.info("Updating {} guest IDs.", incoming.getGuestIds().size());
        List<GuestId> removedGuests = getRemovedGuestIds(existing, incoming);
        List<GuestId> addedGuests = getAddedGuestIds(existing, incoming);

        // Always record a guest ID checkin if the update contained guest IDs. This is
        // used in queries to see which host most recently reported a guest.
        existing.addGuestIdCheckIn();

        List<GuestId> existingGuests = existing.getGuestIds();

        // remove guests that are missing.
        if (existingGuests != null) {
            log.info("removing IDs.");
            for (GuestId guestId : removedGuests) {
                existingGuests.remove(guestId);
                if (log.isDebugEnabled()) {
                    log.info("Guest ID removed: {}", guestId);
                }
                sink.queueEvent(eventFactory.guestIdDeleted(guestId));
            }
        }
        // Check guests that are existing/added.
        for (GuestId guestId : incoming.getGuestIds()) {
            Consumer host = guestHypervisorConsumers.get(guestId.getGuestId());

            if (addedGuests.contains(guestId)) {
                // Add the guestId.
                existing.addGuestId(guestId);
                if (log.isDebugEnabled()) {
                    log.info("New guest ID added: {}", guestId.getGuestId());
                }
                sink.queueEvent(eventFactory.guestIdCreated(guestId));
            }

            // The guest has not registered. No need to process entitlements.
            Consumer guest = guestConsumerMap.get(guestId.getGuestId());
            if (guest == null) {
                continue;
            }

            // Check if the guest was already reported by another host.
            if (host != null && !existing.equals(host)) {
                // If the guest already existed and its host consumer is not the same
                // as the one being updated, then log a warning.
                if (!removedGuests.contains(guestId) && !addedGuests.contains(guestId)) {
                    log.warn("Guest {} is currently being hosted by two hosts: {} and {}",
                        guestId.getGuestId(), existing.getName(), host.getName());
                }

                // Revoke any entitlements related to the other host.
                log.warn("Guest was associated with another host. Revoking " +
                        "invalidated host-specific entitlements related to host: {}", host.getName());

                revokeGuestEntitlementsNotMatchingHost(existing, guest);
            }
            else if (host == null) {
                // now check for any entitlements that may have come from another host
                // that properly reported the guest consumer as going away,
                // and revoke those.
                revokeGuestEntitlementsNotMatchingHost(existing, guest);
            }
        }

        // If nothing shows as being added, and nothing shows as being removed, we should
        // return false here and stop. This is done after the above logic however, as we
        // still need to watch out for multiple hosts reporting the same guest, even if
        // the list they are reporting has not changed.
        if (removedGuests.size() == 0 && addedGuests.size() == 0) {
            return false;
        }

        // Otherwise something must have changed:
        return true;
    }

    private List<GuestId> getAddedGuestIds(Consumer existing, Consumer incoming) {
        return getDifferenceInGuestIds(incoming, existing);
    }

    private List<GuestId> getRemovedGuestIds(Consumer existing, Consumer incoming) {
        return getDifferenceInGuestIds(existing, incoming);
    }

    private List<GuestId> getDifferenceInGuestIds(Consumer c1, Consumer c2) {
        List<GuestId> ids1 = c1.getGuestIds() == null ?
            new ArrayList<GuestId>() : new ArrayList<GuestId>(c1.getGuestIds());
        List<GuestId> ids2 = c2.getGuestIds() == null ?
            new ArrayList<GuestId>() : new ArrayList<GuestId>(c2.getGuestIds());

        List<GuestId> removedGuests = new ArrayList<GuestId>(ids1);
        removedGuests.removeAll(ids2);
        return removedGuests;
    }

    protected void revokeGuestEntitlementsNotMatchingHost(Consumer host, Consumer guest) {
        // we need to create a list of entitlements to delete before actually
        // deleting, otherwise we are tampering with the loop iterator (BZ #786730)
        Set<Entitlement> deletableGuestEntitlements = new HashSet<Entitlement>();
        log.debug("Revoking {} entitlements not matching host: {}", guest, host);
        for (Entitlement entitlement : guest.getEntitlements()) {
            Pool pool = entitlement.getPool();

            // If there is no host required or the pool isn't for unmapped guests, skip it
            if (!(pool.hasAttribute("requires_host") || isUnmappedGuestPool(pool))) {
                continue;
            }

            String requiredHost = getRequiredHost(pool);
            if (isVirtOnly(pool)) {
                if (!requiredHost.equals(host.getUuid())) {
                    log.warn("Removing entitlement {} from guest {}.",
                        entitlement.getPool().getProductId(), guest.getName());
                    deletableGuestEntitlements.add(entitlement);
                }
                else if (isUnmappedGuestPool(pool)) {
                    log.info("Removing unmapped guest pool from {} now that it is mapped", guest.getName());
                    deletableGuestEntitlements.add(entitlement);
                }
            }
            else {
                log.info("Entitlement {} on {} is still valid and will not be removed.",
                    entitlement.getPool().getProductId(), guest.getName());
            }
        }
        // perform the entitlement revocation
        for (Entitlement entitlement : deletableGuestEntitlements) {
            poolManager.revokeEntitlement(entitlement);
        }

        // auto heal guests after revocations
        boolean hasInstalledProducts = guest.getInstalledProducts() != null &&
                !guest.getInstalledProducts().isEmpty();
        if (guest.isAutoheal() && !deletableGuestEntitlements.isEmpty() && hasInstalledProducts) {
            AutobindData autobindData = AutobindData.create(guest).on(new Date());
            List<Entitlement> ents = entitler.bindByProducts(autobindData);
            entitler.sendEvents(ents);
        }


    }

    private String getRequiredHost(Pool pool) {
        return pool.hasAttribute("requires_host") ?
            pool.getAttributeValue("requires_host") : "";
    }

    private boolean isVirtOnly(Pool pool) {
        String virtOnly = pool.hasAttribute("virt_only") ?
            pool.getAttributeValue("virt_only") : "false";
        return virtOnly.equalsIgnoreCase("true") || virtOnly.equals("1");
    }

    private boolean isUnmappedGuestPool(Pool pool) {
        return pool.hasAttribute("unmapped_guests_only") &&
            "true".equals(pool.getAttributeValue("unmapped_guests_only"));
    }

    /**
     * Removes a Consumer
     *
     * @param uuid uuid of the consumer to delete.
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}")
    @Transactional
    public void deleteConsumer(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid,
        @Context Principal principal) {
        log.debug("Deleting consumer_uuid {}", uuid);
        Consumer toDelete = consumerCurator.verifyAndLookupConsumer(uuid);
        try {
            this.poolManager.revokeAllEntitlements(toDelete);
        }
        catch (ForbiddenException e) {
            String msg = e.message().getDisplayMessage();
            throw new ForbiddenException(i18n.tr(
                "Cannot unregister {0} {1} because: {2}", toDelete
                    .getType().getLabel(), toDelete.getName(), msg), e);

        }
        consumerRules.onConsumerDelete(toDelete);

        Event event = eventFactory.consumerDeleted(toDelete);
        consumerCurator.delete(toDelete);
        identityCertService.deleteIdentityCert(toDelete);
        sink.queueEvent(event);
    }

    /**
     * Retrieves a list of Entitlement Certificates for the Consumer
     *
     * @param consumerUuid UUID of the consumer
     * @return a list of EntitlementCertificate  objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("{consumer_uuid}/certificates")
    @Produces(MediaType.APPLICATION_JSON)
    public List<EntitlementCertificate> getEntitlementCertificates(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("serials") String serials) {

        log.debug("Getting client certificates for consumer: {}", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        poolManager.regenerateDirtyEntitlements(entitlementCurator.listByConsumer(consumer));

        Set<Long> serialSet = this.extractSerials(serials);

        List<EntitlementCertificate> returnCerts = new LinkedList<EntitlementCertificate>();
        List<EntitlementCertificate> allCerts = entCertService
            .listForConsumer(consumer);
        for (EntitlementCertificate cert : allCerts) {
            if (serialSet.isEmpty() ||
                serialSet.contains(cert.getSerial().getId())) {
                returnCerts.add(cert);
            }
        }
        return returnCerts;
    }

    /**
     * Retrieves a Compressed File of Entitlement Certificates
     *
     * @return a File of EntitlementCertificate objects
     * @httpcode 500
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces("application/zip")
    @Path("/{consumer_uuid}/certificates")
    public File exportCertificates(
        @Context HttpServletResponse response,
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("serials") String serials) {

        log.debug("Getting client certificate zip file for consumer: {}", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        poolManager.regenerateDirtyEntitlements(entitlementCurator.listByConsumer(consumer));

        Set<Long> serialSet = this.extractSerials(serials);
        // filtering requires a null set, so make this null if it is
        // empty
        if (serialSet.isEmpty()) {
            serialSet = null;
        }

        File archive;
        try {
            archive = exporter.getEntitlementExport(consumer, serialSet);
            response.addHeader("Content-Disposition", "attachment; filename=" +
                archive.getName());

            return archive;
        }
        catch (ExportCreationException e) {
            throw new IseException(
                i18n.tr("Unable to create entitlement certificate archive"), e);
        }
    }

    private Set<Long> extractSerials(String serials) {
        Set<Long> serialSet = new HashSet<Long>();
        if (serials != null) {
            log.debug("Requested serials: {}", serials);
            for (String s : serials.split(",")) {
                log.debug("   {}", s);
                serialSet.add(Long.valueOf(s));
            }
        }

        return serialSet;
    }

    private Set<String> splitKeys(String activationKeyString) {
        Set<String> keys = new LinkedHashSet<String>();
        if (activationKeyString != null) {
            for (String s : activationKeyString.split(",")) {
                keys.add(s);
            }
        }
        return keys;
    }

    /**
     * Retrieves a list of Certiticate Serials
     * <p>
     * Return the client certificate metadata a for the given consumer. This
     * is a small subset of data clients can use to determine which certificates
     * they need to update/fetch.
     *
     * @param consumerUuid UUID of the consumer
     * @return a list of CertificateSerial objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("{consumer_uuid}/certificates/serials")
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "serials")
    public List<CertificateSerialDto> getEntitlementCertificateSerials(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        log.debug("Getting client certificate serials for consumer: {}", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        poolManager.regenerateDirtyEntitlements(entitlementCurator.listByConsumer(consumer));

        List<CertificateSerialDto> allCerts = new LinkedList<CertificateSerialDto>();
        for (EntitlementCertificate cert : entCertService
            .listForConsumer(consumer)) {
            allCerts.add(new CertificateSerialDto(cert.getSerial().getId()));
        }

        return allCerts;
    }

    /**
     * Binds Entitlements
     * <p>
     * If a pool ID is specified, we know we're binding to that exact pool. Specifying
     * an entitle date in this case makes no sense and will throw an error.
     * <p>
     * If a list of product IDs are specified, we attempt to auto-bind to subscriptions
     * which will provide those products. An optional date can be specified allowing
     * the consumer to get compliant for some date in the future. If no date is specified
     * we assume the current date.
     * <p>
     * If neither a pool nor an ID is specified, this is a healing request. The path
     * is similar to the bind by products, but in this case we use the installed products
     * on the consumer, and their current compliant status, to determine which product IDs
     * should be requested. The entitle date is used the same as with bind by products.
     * <p>
     * The Respose will contain a list of Entitlement objects if async is false, or a
     * JobDetail object if async is true.
     *
     * @param consumerUuid Consumer identifier to be entitled
     * @param poolIdString Entitlement pool id.
     * @param email email address.
     * @param emailLocale locale for email address.
     * @param async True if bind should be asynchronous, defaults to false.
     * @param entitleDateStr specific date to entitle by.
     * @return a Response object
     * @httpcode 400
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/entitlements")
    public Response bind(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("pool") @Verify(value = Pool.class, nullable = true,
            subResource = SubResource.ENTITLEMENTS)
                String poolIdString,
        @QueryParam("product") String[] productIds,
        @QueryParam("quantity") Integer quantity,
        @QueryParam("email") String email,
        @QueryParam("email_locale") String emailLocale,
        @QueryParam("async") @DefaultValue("false") boolean async,
        @QueryParam("entitle_date") String entitleDateStr,
        @QueryParam("from_pool") List<String> fromPools) {

        // Check that only one query param was set:
        if (poolIdString != null && productIds != null && productIds.length > 0) {
            throw new BadRequestException(
                i18n.tr("Cannot bind by multiple parameters."));
        }

        if (poolIdString == null && quantity != null) {
            throw new BadRequestException(
                i18n.tr("Cannot specify a quantity when auto-binding."));
        }

        // doesn't make sense to bind by pool and a date.
        if (poolIdString != null && entitleDateStr != null) {
            throw new BadRequestException(
                i18n.tr("Cannot bind by multiple parameters."));
        }

        if (fromPools != null && !fromPools.isEmpty() && poolIdString != null) {
            throw new BadRequestException(
                    i18n.tr("Cannot bind by multiple parameters."));
        }

        // TODO: really should do this in a before we get to this call
        // so the method takes in a real Date object and not just a String.
        Date entitleDate = ResourceDateParser.parseDateString(entitleDateStr);

        // Verify consumer exists:
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        log.debug("Consumer (post verify): {}", consumer);
        try {
            // I hate double negatives, but if they have accepted all
            // terms, we want comeToTerms to be true.
            long subTermsStart = System.currentTimeMillis();
            if (subAdapter.hasUnacceptedSubscriptionTerms(consumer.getOwner())) {
                return Response.serverError().build();
            }
            log.info("Checked if consumer has unaccepted subscription terms in {}ms",
                    (System.currentTimeMillis() - subTermsStart));
        }
        catch (CandlepinException e) {
            log.debug(e.getMessage());
            throw e;
        }

        if (poolIdString != null && quantity == null) {
            Pool pool = poolManager.find(poolIdString);
            if (pool != null) {
                quantity = consumerBindUtil.getQuantityToBind(pool, consumer);
            }
            else {
                quantity = 1;
            }
        }

        //
        // HANDLE ASYNC
        //
        if (async) {
            JobDetail detail = null;

            if (poolIdString != null) {
                detail = EntitlerJob.bindByPool(poolIdString, consumer, quantity);
            }
            else {
                detail = EntitleByProductsJob.bindByProducts(productIds,
                        consumer, entitleDate, fromPools);
            }

            // events will be triggered by the job
            return Response.status(Response.Status.OK)
                .type(MediaType.APPLICATION_JSON).entity(detail).build();
        }

        //
        // otherwise we do what we do today.
        //
        List<Entitlement> entitlements = null;

        if (poolIdString != null) {
            entitlements = entitler.bindByPool(poolIdString, consumer, quantity);
        }
        else {
            AutobindData autobindData = AutobindData.create(consumer).on(entitleDate)
                    .forProducts(productIds).withPools(fromPools);
            entitlements = entitler.bindByProducts(autobindData);
        }

        // Trigger events:
        entitler.sendEvents(entitlements);

        return Response.status(Response.Status.OK)
            .type(MediaType.APPLICATION_JSON).entity(entitlements).build();
    }

    /**
     * Retrieves a list of Pools and quantities that would be the result of an auto-bind.
     * <p>
     * This is a dry run of an autobind. It allows the client to see what would be the
     * result of an autobind without executing it. It can only do this for the prevously
     * established list of installed products for the consumer
     * <p>
     * If a service level is included in the request, then that level will override the
     * one stored on the consumer. If no service level is included then the existing
     * one will be used.
     * <p>
     * The Response has a list of PoolQuantity objects
     *
     * @param consumerUuid Consumer identifier to be entitled
     * @param serviceLevel String service level override to be used for run
     * @return a Response object
     * @httpcode 400
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/entitlements/dry-run")
    public List<PoolQuantity> dryBind(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("service_level") String serviceLevel) {

        // Verify consumer exists:
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        List<PoolQuantity> dryRunPools = new ArrayList<PoolQuantity>();

        try {
            consumerBindUtil.validateServiceLevel(consumer.getOwner(), serviceLevel);
            dryRunPools = entitler.getDryRun(consumer, serviceLevel);
            if (dryRunPools == null) {
                return new ArrayList<PoolQuantity>();
            }
        }
        catch (ForbiddenException fe) {
            return dryRunPools;
        }
        catch (BadRequestException bre) {
            throw bre;
        }
        catch (RuntimeException re) {
            return dryRunPools;
        }

        return dryRunPools;
    }

    private Entitlement verifyAndLookupEntitlement(String entitlementId) {
        Entitlement entitlement = entitlementCurator.find(entitlementId);

        if (entitlement == null) {
            throw new NotFoundException(i18n.tr(
                "Entitlement with ID ''{0}'' could not be found.", entitlementId));
        }
        return entitlement;
    }

    /**
     * Retrives a list of Entitlements
     *
     * @return a list of Entitlement objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/entitlements")
    @Paginate
    public List<Entitlement> listEntitlements(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("product") String productId,
        @QueryParam("regen") @DefaultValue("true") Boolean regen,
        @QueryParam("matches") String matches,
        @QueryParam("attribute") @CandlepinParam(type = KeyValueParameter.class)
        List<KeyValueParameter> attrFilters,
        @Context PageRequest pageRequest) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Page<List<Entitlement>> entitlementsPage;
        // No need to add filters when matching by product.
        if (productId != null) {
            Product p = productCurator.lookupById(consumer.getOwner(), productId);
            if (p == null) {
                throw new BadRequestException(i18n.tr(
                    "Product with ID ''{0}'' could not be found.", productId));
            }
            entitlementsPage = entitlementCurator.listByConsumerAndProduct(consumer,
                productId, pageRequest);
        }
        else {
            // Build up any provided entitlement filters from query params.
            EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
            for (KeyValueParameter filterParam : attrFilters) {
                filters.addAttributeFilter(filterParam.key(), filterParam.value());
            }
            if (!StringUtils.isEmpty(matches)) {
                filters.addMatchesFilter(matches);
            }
            entitlementsPage = entitlementCurator.listByConsumer(consumer, filters, pageRequest);
        }

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, entitlementsPage);

        if (regen) {
            poolManager.regenerateDirtyEntitlements(entitlementsPage.getPageData());
        }
        else {
            log.debug("Skipping certificate regeneration.");
        }

        return entitlementsPage.getPageData();
    }

    /**
     * Retrieves the Owner associated to a Consumer
     *
     * @return an Owner object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/owner")
    public Owner getOwner(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        return consumer.getOwner();
    }

    /**
     * Unbinds all Entitlements for a Consumer
     * <p>
     * Result contains the total number of entitlements unbound.
     *
     * @param consumerUuid Unique id for the Consumer.
     * @return a DeleteResult object
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/entitlements")
    public DeleteResult unbindAll(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        // FIXME: just a stub, needs CertifcateService (and/or a
        // CertificateCurator) to lookup by serialNumber
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        if (consumer == null) {
            throw new NotFoundException(i18n.tr(
                "Unit with ID ''{0}'' could not be found.", consumerUuid));
        }

        int total = poolManager.revokeAllEntitlements(consumer);
        log.debug("Revoked {} entitlements from {}", total, consumerUuid);
        return new DeleteResult(total);

        // Need to parse off the value of subscriptionNumberArgs, probably
        // use comma separated see IntergerList in sparklines example in
        // jersey examples find all entitlements for this consumer and
        // subscription numbers delete all of those (and/or return them to
        // entitlement pool)
    }

    /**
     * Removes an Entitlement from a Consumer
     * <p>
     * By the Entitlement ID
     *
     * @param dbid the entitlement to delete.
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{consumer_uuid}/entitlements/{dbid}")
    public void unbind(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @PathParam("dbid") @Verify(Entitlement.class) String dbid,
        @Context Principal principal) {

        consumerCurator.verifyAndLookupConsumer(consumerUuid);

        Entitlement toDelete = entitlementCurator.find(dbid);
        if (toDelete != null) {
            poolManager.revokeEntitlement(toDelete);
            return;
        }

        throw new NotFoundException(i18n.tr(
            "Entitlement with ID ''{0}'' could not be found.", dbid
        ));
    }

    /**
     * Removes an Entitlement from a Consumer
     * <p>
     * By the Certificate Serial
     *
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{consumer_uuid}/certificates/{serial}")
    public void unbindBySerial(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @PathParam("serial") Long serial) {

        consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Entitlement toDelete = entitlementCurator
            .findByCertificateSerial(serial);

        if (toDelete != null) {
            poolManager.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(i18n.tr(
            "Entitlement Certificate with serial number ''{0}'' could not be found.",
            serial.toString())); // prevent serial number formatting.
    }

    /**
     * Retrieves a list of Consumer Events
     *
     * @return a list of Event objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}/events")
    public List<Event> getConsumerEvents(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        List<Event> events = this.eventCurator.listMostRecent(FEED_LIMIT,
            consumer);
        if (events != null) {
            eventAdapter.addMessageText(events);
        }
        return events;
    }

    /**
     * Retrieves and Event Atom Feed for a Consumer
     *
     * @return a Feed object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces("application/atom+xml")
    @Path("/{consumer_uuid}/atom")
    public Feed getConsumerAtomFeed(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        String path = String.format("/consumers/%s/atom", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Feed feed = this.eventAdapter.toFeed(
            this.eventCurator.listMostRecent(FEED_LIMIT, consumer), path);
        feed.setTitle("Event feed for consumer " + consumer.getUuid());
        return feed;
    }

    /**
     * Regenerates the Entitlement Certificates for a Consumer
     *
     * @httpcode 404
     * @httpcode 200
     */
    @PUT
    @Path("/{consumer_uuid}/certificates")
    public void regenerateEntitlementCertificates(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("entitlement") String entitlementId,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {
        if (entitlementId != null) {
            Entitlement e = verifyAndLookupEntitlement(entitlementId);
            poolManager.regenerateCertificatesOf(e, false, lazyRegen);
        }
        else {
            Consumer c = consumerCurator.verifyAndLookupConsumer(consumerUuid);
            poolManager.regenerateEntitlementCertificates(c, lazyRegen);
        }
    }

    /**
     * Retrieves a Compressed File representation of a Consumer
     *
     * @return a File
     * @httpcode 403
     * @httpcode 500
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces("application/zip")
    @Path("{consumer_uuid}/export")
    public File exportData(
        @Context HttpServletResponse response,
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("cdn_label") String cdnLabel,
        @QueryParam("webapp_prefix") String webAppPrefix,
        @QueryParam("api_url") String apiUrl) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.getType() == null ||
            !consumer.getType().isManifest()) {
            throw new ForbiddenException(
                i18n.tr(
                    "Unit {0} cannot be exported. " +
                    "A manifest cannot be made for units of type ''{1}''.",
                    consumerUuid, consumer.getType().getLabel()));
        }

        if (!StringUtils.isBlank(cdnLabel) &&
            cdnCurator.lookupByLabel(cdnLabel) == null) {
            throw new ForbiddenException(
                i18n.tr("A CDN with label {0} does not exist on this system.", cdnLabel));
        }

        poolManager.regenerateDirtyEntitlements(entitlementCurator.listByConsumer(consumer));

        File archive;
        try {
            archive = exporter.getFullExport(consumer, cdnLabel, webAppPrefix, apiUrl);
            response.addHeader("Content-Disposition", "attachment; filename=" +
                archive.getName());

            sink.queueEvent(eventFactory.exportCreated(consumer));
            return archive;
        }
        catch (ExportCreationException e) {
            throw new IseException(i18n.tr("Unable to create export archive"),
                e);
        }
    }

    /**
     * Retrieves a single Consumer
     *
     * @param uuid uuid of the consumer sought.
     * @return a Consumer object
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}")
    public Consumer regenerateIdentityCertificates(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid) {

        Consumer c = consumerCurator.verifyAndLookupConsumer(uuid);
        EventBuilder eventBuilder = eventFactory
                .getEventBuilder(Target.CONSUMER, Type.MODIFIED)
                .setOldEntity(c);

        IdentityCertificate ic = generateIdCert(c, true);
        c.setIdCert(ic);
        consumerCurator.update(c);
        sink.queueEvent(eventBuilder.setNewEntity(c).buildEvent());
        return c;
    }

    /**
     * Generates the identity certificate for the given consumer and user.
     * Throws RuntimeException if there is a problem with generating the
     * certificate.
     * <p>
     * Regenerating an Id Cert is ok to do at any time. Since we only check
     * that the cert's date range is valid, and that it is signed by us,
     * and that the consumer UUID is in our db, it doesn't matter if the actual
     * cert itself is the one stored in our db (and therefore the most recent
     * version) or not.
     *
     * @param c Consumer whose certificate needs to be generated.
     * @param regen if true, forces a regen of the certificate.
     * @return an IdentityCertificate object
     */
    private IdentityCertificate generateIdCert(Consumer c, boolean regen) {
        IdentityCertificate idCert = null;
        boolean errored = false;

        try {
            if (regen) {
                idCert = identityCertService.regenerateIdentityCert(c);
            }
            else {
                idCert = identityCertService.generateIdentityCert(c);
            }

            if (idCert == null) {
                errored = true;
            }
        }
        catch (GeneralSecurityException e) {
            log.error("Problem regenerating ID cert for unit:", e);
            errored = true;
        }
        catch (IOException e) {
            log.error("Problem regenerating ID cert for unit:", e);
            errored = true;
        }

        if (errored) {
            throw new BadRequestException(i18n.tr(
                "Problem regenerating ID cert for unit {0}", c));
        }

        log.debug("Generated identity cert: {}", idCert.getSerial());

        return idCert;
    }

    /**
     * Retrieves a list of Guest Consumers of a Consumer
     *
     * @return a list of Consumer objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/guests")
    public List<Consumer> getGuests(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        return consumerCurator.getGuests(consumer);
    }

    /**
     * Retrieves a Host Consumer of a Consumer
     *
     * @return a Consumer object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/host")
    public Consumer getHost(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.getFact("virt.uuid") == null ||
            consumer.getFact("virt.uuid").trim().equals("")) {
            throw new BadRequestException(i18n.tr(
                "The system with UUID {0} is not a virtual guest.",
                consumer.getUuid()));
        }
        return consumerCurator.getHost(consumer.getFact("virt.uuid"), consumer.getOwner());
    }

    /**
     * Retrieves the Release of a Consumer
     *
     * @param consumerUuid
     * @return a Release object
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/release")
    public Release getRelease(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.getReleaseVer() != null) {
            return consumer.getReleaseVer();
        }
        return new Release("");
    }

    /**
     * Retireves the Compliance Status of a Consumer.
     *
     * @param uuid uuid of the consumer to get status for.
     * @param onDate Date to get compliance information for, default is now.
     * @return a ComplianceStatus object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}/compliance")
    @Transactional
    public ComplianceStatus getComplianceStatus(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid,
        @QueryParam("on_date") String onDate) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);
        Date date = ResourceDateParser.parseDateString(onDate);
        return this.complianceRules.getStatus(consumer, date);
    }

    /**
     * Retrieves a Compliance Status list for a Consumer
     *
     * @param uuids
     * @return a list of ComplianceStatus objects
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/compliance")
    @Transactional
    public Map<String, ComplianceStatus> getComplianceStatusList(
        @QueryParam("uuid") @Verify(value = Consumer.class, nullable = true)
            List<String> uuids) {
        List<Consumer> consumers = uuids == null ? new LinkedList<Consumer>() :
            consumerCurator.findByUuids(uuids);
        Map<String, ComplianceStatus> results = new HashMap<String, ComplianceStatus>();

        for (Consumer consumer : consumers) {
            ComplianceStatus status = complianceRules.getStatus(consumer, null);
            results.put(consumer.getUuid(), status);
        }

        return results;
    }

    private void addDataToInstalledProducts(Consumer consumer) {

        if (consumer.getInstalledProducts().size() == 0) {
            /*
             * No installed products implies nothing to enrich.
             *
             * Distributors can have many entitlements, but no installed products.
             * Calculating the status can be quite expensive but the data isn't
             * actually used if there's nothing installed.
             */
            return;
        }

        ComplianceStatus complianceStatus = complianceRules.getStatus(consumer, null, false);

        ConsumerInstalledProductEnricher enricher = new ConsumerInstalledProductEnricher(
            consumer, complianceStatus, complianceRules
        );

        for (ConsumerInstalledProduct cip : consumer.getInstalledProducts()) {
            String prodId = cip.getProductId();
            Product prod = this.productCurator.lookupById(consumer.getOwner(), prodId);

            if (prod != null) {
                enricher.enrich(cip, prod);
            }
        }
    }
    /**
     * Removes the Deletion Record for a Consumer
     * <p>
     * Allowed for a superadmin. The main use case for this would be if
     * a user accidently deleted a non-RHEL hypervisor, causing it to no
     * longer be auto-detected via virt-who.
     *
     * @param uuid
     *
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("{consumer_uuid}/deletionrecord")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public void removeDeletionRecord(@PathParam("consumer_uuid") String uuid) {
        DeletedConsumer dc = deletedConsumerCurator.findByConsumerUuid(uuid);
        if (dc == null) {
            throw new NotFoundException(i18n.tr(
                "Deletion record for hypervisor ''{0}'' not found.", uuid));
        }
        deletedConsumerCurator.delete(dc);
    }

    private void addCalculatedAttributes(Entitlement ent) {
        // With no consumer/date, this will not build suggested quantity
        Map<String, String> calculatedAttributes =
            calculatedAttributesUtil.buildCalculatedAttributes(ent.getPool(), null);
        ent.getPool().setCalculatedAttributes(calculatedAttributes);
    }

}
