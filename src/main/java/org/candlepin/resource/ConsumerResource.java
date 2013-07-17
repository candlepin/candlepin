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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;
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
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventAdapter;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.ActivationKeyCurator;
import org.candlepin.model.ActivationKeyPool;
import org.candlepin.model.CertificateSerialDto;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeleteResult;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.EventCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.User;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.Paginate;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.util.ConsumerInstalledProductEnricher;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.sync.ExportCreationException;
import org.candlepin.sync.Exporter;
import org.candlepin.util.Util;
import org.candlepin.version.CertVersionConflictException;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

/**
 * API Gateway for Consumers
 */
@Path("/consumers")
public class ConsumerResource {
    private Pattern consumerSystemNamePattern;
    private Pattern consumerPersonNamePattern;

    private static Logger log = Logger.getLogger(ConsumerResource.class);
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductServiceAdapter productAdapter;
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
    private Config config;

    @Inject
    public ConsumerResource(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        ProductServiceAdapter productAdapter,
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
        Config config) {

        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productAdapter = productAdapter;
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
        this.consumerPersonNamePattern = Pattern.compile(config.getString(
            ConfigProperties.CONSUMER_PERSON_NAME_PATTERN));
        this.consumerSystemNamePattern = Pattern.compile(config.getString(
            ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN));
        this.config = config;
    }
    /**
     * List available Consumers
     *
     * @return list of available consumers.
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "consumers")
    @Paginate
    public List<Consumer> list(@QueryParam("username") String userName,
        @QueryParam("type") String typeLabel,
        @QueryParam("owner") String ownerKey,
        @Context PageRequest pageRequest) {
        ConsumerType type = null;

        if (typeLabel != null) {
            type = lookupConsumerType(typeLabel);
        }

        Owner owner = null;
        if (ownerKey != null) {
            owner = ownerCurator.lookupByKey(ownerKey);

            if (owner == null) {
                throw new NotFoundException(
                    i18n.tr("Organization with key: {0} was not found.",
                        ownerKey));
            }
        }

        // We don't look up the user and warn if it doesn't exist here to not
        // give away usernames
        Page<List<Consumer>> p = consumerCurator.listByUsernameAndType(userName,
            type, owner, pageRequest);

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, p);
        return p.getPageData();
    }

    /**
     * Return the consumer identified by the given uuid.
     *
     * @param uuid uuid of the consumer sought.
     * @return the consumer identified by the given uuid.
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}")
    public Consumer getConsumer(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid) {
        Consumer consumer = verifyAndLookupConsumer(uuid);

        if (consumer != null) {
            IdentityCertificate idcert = consumer.getIdCert();
            Date expire = idcert.getSerial().getExpiration();
            int days = config.getInt(ConfigProperties.IDENTITY_CERT_EXPIRY_THRESHOLD, 90);
            Date futureExpire = Util.addDaysToDt(days);
            // if expiration is within 90 days, regenerate it
            if (log.isDebugEnabled()) {
                log.debug("Threshold [" + days + "] expires on [" + expire +
                    "] futureExpire [" + futureExpire + "]");
            }

            if (expire.before(futureExpire)) {
                log.warn("regenerating certificate for [" + uuid + "]");
                consumer = this.regenerateIdentityCertificates(uuid);
            }

            // enrich with subscription data
            consumer.setCanActivate(subAdapter
                .canActivateSubscription(consumer));
            // enrich with installed product data
            addDataToInstalledProducts(consumer);
        }

        return consumer;
    }

    /**
     * Create a Consumer. NOTE: Opening this method up to everyone, as we have
     * nothing we can reliably verify in the method signature. Instead we have
     * to figure out what owner this consumer is destined for (due to backward
     * compatability with existing clients which do not specify an owner during
     * registration), and then check the access to the specified owner in the
     * method itself.
     *
     * @param consumer Consumer metadata
     * @return newly created Consumer
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
        @QueryParam("activation_keys") String activationKeys)
        throws BadRequestException {
        // API:registerConsumer
        Set<String> keyStrings = splitKeys(activationKeys);

        // Only let NoAuth principals through if there are activation keys to
        // consider:
        if ((principal instanceof NoAuthPrincipal) && (keyStrings.size() == 0)) {
            throw new ForbiddenException(i18n.tr("Insufficient permissions"));
        }

        if (keyStrings.size() > 0) {
            if (ownerKey == null) {
                throw new BadRequestException(
                    i18n.tr("Must specify an org to register with activation keys."));
            }
            if (userName != null) {
                throw new BadRequestException(
                    i18n.tr("Cannot specify username with activation keys."));
            }
        }

        Owner owner = setupOwner(principal, ownerKey);
        // Raise an exception if any keys were specified which do not exist
        // for this owner.
        List<ActivationKey> keys = new ArrayList<ActivationKey>();
        if (keyStrings.size() > 0) {
            for (String keyString : keyStrings) {
                ActivationKey key = findKey(keyString, owner);
                keys.add(key);
            }
        }

        userName = setUserName(consumer, principal, userName);

        checkConsumerName(consumer);

        ConsumerType type = lookupConsumerType(consumer.getType().getLabel());
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            if (keys.size() > 0) {
                throw new BadRequestException(
                    i18n.tr("A consumer type of 'person' cannot be" +
                        " used with activation keys"));
            }
            if (!isConsumerPersonNameValid(consumer.getName())) {
                throw new BadRequestException(
                    i18n.tr("System name cannot contain most special characters."));
            }

            verifyPersonConsumer(consumer, type, owner, userName);
        }

        if (type.isType(ConsumerTypeEnum.SYSTEM)) {
            if (!isConsumerSystemNameValid(consumer.getName())) {
                throw new BadRequestException(
                    i18n.tr("System name cannot contain most special characters."));
            }
        }
        consumer.setOwner(owner);
        consumer.setType(type);
        consumer.setCanActivate(subAdapter.canActivateSubscription(consumer));
        consumer.setAutoheal(true); // this is the default
        if (consumer.getServiceLevel() == null) {
            consumer.setServiceLevel("");
        }

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
        }

        checkServiceLevel(owner, consumer.getServiceLevel());

        try {
            consumer = consumerCurator.create(consumer);
            IdentityCertificate idCert = generateIdCert(consumer, false);
            consumer.setIdCert(idCert);

            sink.emitConsumerCreated(consumer);

            // Process activation keys.
            for (ActivationKey ak : keys) {
                for (ActivationKeyPool akp : ak.getPools()) {
                    List<Entitlement> entitlements = null;

                    String poolId = Util.assertNotNull(akp.getPool().getId(),
                        i18n.tr("Pool ID must be provided"));
                    entitlements = entitler.bindByPool(poolId, consumer, akp
                        .getQuantity().intValue());

                    // Trigger events:
                    entitler.sendEvents(entitlements);
                }
            }

            ComplianceStatus compliance = complianceRules.getStatus(consumer,
                Calendar.getInstance().getTime());
            consumer.setEntitlementStatus(compliance.getStatus());
            consumerCurator.update(consumer);

            return consumer;
        }
        catch (CandlepinException ce) {
            // If it is one of ours, rethrow it.
            throw ce;
        }
        catch (Exception e) {
            log.error("Problem creating consumer:", e);
            e.printStackTrace();
            throw new BadRequestException(i18n.tr(
                "Problem creating consumer {0}", consumer));
        }
    }
    /**
     * @param consumer
     * @param principal
     * @param userName
     * @return
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
     * @return
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
        return change;
    }

    /**
     * @param consumer
     * @param principal
     * @param userName
     * @return
     */
    private void checkConsumerName(Consumer consumer) {

        if (consumer.getName() == null) {
            throw new BadRequestException(
                i18n.tr("System name cannot be null."));
        }

        // for now this applies to both types consumer
        if (consumer.getName().indexOf('#') == 0) {
            // this is a bouncycastle restriction
            throw new BadRequestException(
                i18n.tr("System name cannot begin with # character"));
        }
    }

    private void checkServiceLevel(Owner owner, String serviceLevel)
        throws BadRequestException {
        if (serviceLevel != null &&
            !serviceLevel.trim().equals("")) {
            for (String level : poolManager.retrieveServiceLevelsForOwner(owner, false)) {
                if (serviceLevel.equalsIgnoreCase(level)) {
                    return;
                }
            }
            throw new BadRequestException(
                i18n.tr(
                    "Service level ''{0}'' is not available " +
                    "to consumers of organization {1}.",
                    serviceLevel, owner.getKey()));
        }
    }

    private void logNewConsumerDebugInfo(Consumer consumer,
        List<ActivationKey> keys, ConsumerType type) {
        if (log.isDebugEnabled()) {
            log.debug("Got consumerTypeLabel of: " + type.getLabel());
            log.debug("got facts: \n" + consumer.getFacts());

            if (consumer.getFacts() != null) {
                for (String key : consumer.getFacts().keySet()) {
                    log.debug("   " + key + " = " + consumer.getFact(key));
                }
            }

            log.debug("Activation keys:");
            for (ActivationKey activationKey : keys) {
                log.debug("   " + activationKey.getName());
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
        Owner owner, String username) {

        User user = null;
        try {
            user = userService.findByLogin(username);
        }
        catch (UnsupportedOperationException e) {
            log.warn("User service does not allow user lookups, " +
                "cannot verify person consumer.");
        }

        if (user == null) {
            throw new NotFoundException(i18n.tr("No such user: {0}"));
        }

        // When registering person consumers we need to be sure the username
        // has some association with the owner the consumer is destined for:
        if (!user.hasOwnerAccess(owner, Access.ALL) && !user.isSuperAdmin()) {
            throw new ForbiddenException(i18n.tr(
                "User {0} has no roles for organization {1}",
                user.getUsername(), owner.getKey()));
        }

        // TODO: Refactor out type specific checks?
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            Consumer existing = consumerCurator.findByUser(user);

            if (existing != null &&
                existing.getType().isType(ConsumerTypeEnum.PERSON)) {
                // TODO: This is not the correct error code for this situation!
                throw new BadRequestException(i18n.tr(
                    "User {0} has already registered a personal consumer",
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
                    i18n.tr("You must specify an organization for new consumers."));
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
        if ((principal instanceof UserPrincipal)) {
            if (!principal.canAccess(owner, Access.ALL)) {
                throw new ForbiddenException(i18n.tr(
                    "User {0} cannot access organization {1}",
                    principal.getPrincipalName(), owner.getKey()));
            }
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
                log.info("Creating new owner: " + owner.getKey());
                existingOwner = ownerCurator.create(owner);
                poolManager.getRefresher().add(existingOwner).run();
            }
        }
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
        Consumer toUpdate = verifyAndLookupConsumer(uuid);

        if (performConsumerUpdates(consumer, toUpdate)) {
            consumerCurator.update(toUpdate);
        }
    }

    // Requires security hole since security interceptor will intercept when the method is
    // called. This is because it is protected. This method is called from other resources,
    // and therefore it assumes the caller is screened first.
    // TODO Might be a better way to do this.
    @SecurityHole(noAuth = true)
    protected boolean performConsumerUpdates(Consumer updated, Consumer toUpdate) {
        if (log.isDebugEnabled()) {
            log.debug("Updating consumer: " + toUpdate.getUuid());
        }
        // We need a representation of the consumer before making any modifications.
        // If nothing changes we won't send.
        Event event = eventFactory.consumerModified(toUpdate, updated);

        // version changed on non-checked in consumer, or list of capabilities
        // changed on checked in consumer
        boolean changesMade = updateCapabilities(toUpdate, updated);

        changesMade = checkForFactsUpdate(toUpdate, updated) || changesMade;
        changesMade = checkForInstalledProductsUpdate(toUpdate, updated) || changesMade;
        changesMade = checkForGuestsUpdate(toUpdate, updated) || changesMade;

        // Allow optional setting of the autoheal attribute:
        if (updated.isAutoheal() != null &&
             !updated.isAutoheal().equals(toUpdate.isAutoheal())) {
            if (log.isDebugEnabled()) {
                log.debug("   Updating consumer autoheal setting.");
            }
            toUpdate.setAutoheal(updated.isAutoheal());
            changesMade = true;
        }

        if (updated.getReleaseVer() != null &&
            (updated.getReleaseVer().getReleaseVer() != null) &&
            !updated.getReleaseVer().equals(toUpdate.getReleaseVer())) {
            if (log.isDebugEnabled()) {
                log.debug("   Updating consumer releaseVer setting.");
            }
            toUpdate.setReleaseVer(updated.getReleaseVer());
            changesMade = true;
        }

        // Allow optional setting of the service level attribute:
        String level = updated.getServiceLevel();
        if (level != null &&
            !level.equals(toUpdate.getServiceLevel())) {
            if (log.isDebugEnabled()) {
                log.debug("   Updating consumer service level setting.");
            }
            checkServiceLevel(toUpdate.getOwner(), level);
            toUpdate.setServiceLevel(level);
            changesMade = true;
        }

        String environmentId =
            updated.getEnvironment() == null ? null : updated.getEnvironment().getId();
        if (environmentId != null && (toUpdate.getEnvironment() == null ||
                    !toUpdate.getEnvironment().getId().equals(environmentId))) {
            Environment e = environmentCurator.find(environmentId);
            if (e == null) {
                throw new NotFoundException(i18n.tr("No such environment: {0}",
                                            environmentId));
            }
            toUpdate.setEnvironment(e);

            // lazily regenerate certs, so the client can still work
            poolManager.regenerateEntitlementCertificates(toUpdate, true);
            changesMade = true;
        }

        // like the other values in an update, if consumer name is null, act as if
        // it should remain the same
        if (updated.getName() != null && !toUpdate.getName().equals(updated.getName())) {
            checkConsumerName(updated);
            toUpdate.setName(updated.getName());

            // get the new name into the id cert
            IdentityCertificate ic = generateIdCert(toUpdate, true);
            toUpdate.setIdCert(ic);
        }

        if (updated.getLastCheckin() != null) {
            toUpdate.setLastCheckin(updated.getLastCheckin());
            changesMade = true;
        }

        if (changesMade) {
            log.info("Consumer " + toUpdate.getUuid() + " updated.");

            ComplianceStatus compliance = complianceRules.getStatus(toUpdate,
                Calendar.getInstance().getTime());
            toUpdate.setEntitlementStatus(compliance.getStatus());

            // Set the updated date here b/c @PreUpdate will not get fired
            // since only the facts table will receive the update.
            toUpdate.setUpdated(new Date());
            sink.sendEvent(event);
        }
        return changesMade;
    }

    /**
     * Check if the consumers facts have changed. If they do not appear to have been
     * specified in this PUT, skip updating facts entirely.
     *
     * @param existing existing consumer
     * @param incoming incoming consumer
     * @return True if facts were included in request and have changed.
     */
    private boolean checkForFactsUpdate(Consumer existing, Consumer incoming) {
        if (incoming.getFacts() == null) {
            if (log.isDebugEnabled()) {
                log.debug("Facts not included in this consumer update, skipping update.");
            }
            return false;
        }
        else if (!existing.factsAreEqual(incoming)) {
            if (log.isDebugEnabled()) {
                log.debug("Updating consumer facts.");
            }
            existing.setFacts(incoming.getFacts());
            return true;
        }
        return false;
    }

    /**
     * Check if the consumers installed products have changed. If they do not appear to
     * have been specified in this PUT, skip updating installed products entirely.
     *
     * @param existing existing consumer
     * @param incoming incoming consumer
     * @return True if installed products were included in request and have changed.
     */
    private boolean checkForInstalledProductsUpdate(Consumer existing, Consumer incoming) {

        if (incoming.getInstalledProducts() == null) {
            if (log.isDebugEnabled()) {
                log.debug("Installed packages not included in this consumer update, " +
                    "skipping update.");
            }
            return false;
        }
        else if (!existing.getInstalledProducts().equals(incoming.getInstalledProducts())) {
            if (log.isDebugEnabled()) {
                log.debug("Updating installed products.");
            }
            existing.getInstalledProducts().clear();
            for (ConsumerInstalledProduct cip : incoming.getInstalledProducts()) {
                existing.addInstalledProduct(cip);
            }
            return true;
        }
        if (log.isDebugEnabled()) {
            log.debug("No change to installed products.");
        }
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
     *
     * @param existing existing consumer
     * @param incoming incoming consumer
     * @return True if guest IDs were included in request and have changed.
     */
    private boolean checkForGuestsUpdate(Consumer existing, Consumer incoming) {

        if (incoming.getGuestIds() == null) {
            if (log.isDebugEnabled()) {
                log.debug("Guests not included in this consumer update, " +
                    "skipping update.");
            }
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("Updating consumer's guest IDs.");
        }
        List<GuestId> removedGuests = getRemovedGuestIds(existing, incoming);
        List<GuestId> addedGuests = getAddedGuestIds(existing, incoming);

        // Ensure that existing actually has guest ids initialized.
        if (existing.getGuestIds() != null) {
            // Always clear existing id so that the timestamps are updated
            // on each ID.
            existing.getGuestIds().clear();
        }

        if (log.isDebugEnabled()) {
            log.debug("Updating guest entitlements.");
        }

        // Check guests that are existing/added.
        for (GuestId guestId : incoming.getGuestIds()) {
            Consumer host = consumerCurator.getHost(guestId.getGuestId());
            Consumer guest = consumerCurator.findByVirtUuid(guestId.getGuestId(),
                existing.getOwner().getId());

            // Add back the guestId.
            existing.addGuestId(guestId);

            // If adding a new GuestId send notification.
            if (addedGuests.contains(guestId)) {
                if (log.isDebugEnabled()) {
                    log.debug("New guest ID added: " + guestId.getGuestId());
                }
                sink.sendEvent(eventFactory.guestIdCreated(existing, guestId));
            }

            // The guest has not registered. No need to process entitlements.
            if (guest == null) {
                continue;
            }

            // Check if the guest was already reported by another host.
            if (host != null && !existing.equals(host)) {
                // If the guest already existed and its host consumer is not the same
                // as the one being updated, then log a warning.
                if (!removedGuests.contains(guestId) && !addedGuests.contains(guestId)) {
                    log.warn("Guest " + guestId.getGuestId() +
                        " is currently being hosted by two hosts: " +
                        existing.getName() + " " + host.getName());
                }

                // Revoke any entitlements related to the other host.
                log.warn("Guest was associated with another host. Revoking " +
                        "invalidated host-specific entitlements related to host: " +
                        host.getName());

                revokeGuestEntitlementsNotMatchingHost(existing, guest);
                // commented out per mkhusid (see 768872, around comment #41)
                /*
                // now autosubscribe to the new host. We bypass bind() since we
                // are being invoked via the host, not the guest.

                // only attempt this if there are installed products, otherwise there
                // is nothing to bind to
                if (guest.getInstalledProducts() == null ||
                    guest.getInstalledProducts().isEmpty()) {
                    log.debug("No installed products for guest, unable to autosubscribe");
                }
                else {
                    log.debug("Autosubscribing migrated guest.");
                    List<Entitlement> entitlements =  entitler.bindByProducts(
                                                                    null, guest, null);
                    entitler.sendEvents(entitlements);
                }*/
            }
            else if (host == null) {
                // now check for any entitlements that may have come from another host
                // that properly reported the guest consumer as going away,
                // and revoke those.
                revokeGuestEntitlementsNotMatchingHost(existing, guest);
            }
        }

        // Check guests that have been removed.
        for (GuestId guestId : removedGuests) {
            // Report that the guestId was removed.
            if (log.isDebugEnabled()) {
                log.debug("Guest ID removed: " + guestId.getGuestId());
            }
            sink.sendEvent(eventFactory.guestIdDeleted(existing, guestId));

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

    private void revokeGuestEntitlementsNotMatchingHost(Consumer host, Consumer guest) {
        // we need to create a list of entitlements to delete before actually
        // deleting, otherwise we are tampering with the loop iterator (BZ #786730)
        Set<Entitlement> deletableGuestEntitlements = new HashSet<Entitlement>();
        for (Entitlement entitlement : guest.getEntitlements()) {
            Pool pool = entitlement.getPool();

            // If there is no host required, do not revoke the entitlement.
            if (!pool.hasAttribute("requires_host")) {
                continue;
            }

            String requiredHost = getRequiredHost(pool);
            if (isVirtOnly(pool) && !requiredHost.equals(host.getUuid())) {
                log.warn("Removing entitlement " + entitlement.getProductId() +
                    " from guest " + guest.getName());
                deletableGuestEntitlements.add(entitlement);
            }
            else {
                log.info("Entitlement " + entitlement.getProductId() +
                         " on " + guest.getName() +
                         " is still valid, and will not be removed.");
            }
        }
        // perform the entitlement revocation
        for (Entitlement entitlement : deletableGuestEntitlements) {
            poolManager.revokeEntitlement(entitlement);
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

    /**
     * delete the consumer.
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
        if (log.isDebugEnabled()) {
            log.debug("deleting  consumer_uuid" + uuid);
        }
        Consumer toDelete = verifyAndLookupConsumer(uuid);
        try {
            this.poolManager.revokeAllEntitlements(toDelete);
        }
        catch (ForbiddenException e) {
            String msg = e.message().getDisplayMessage();
            throw new ForbiddenException(i18n.tr(
                "Cannot unregister {0} consumer {1} because: {2}", toDelete
                    .getType().getLabel(), toDelete.getName(), msg), e);

        }
        consumerRules.onConsumerDelete(toDelete);

        Event event = eventFactory.consumerDeleted(toDelete);
        consumerCurator.delete(toDelete);
        identityCertService.deleteIdentityCert(toDelete);
        sink.sendEvent(event);
    }

    /**
     * Return the entitlement certificate for the given consumer.
     *
     * @param consumerUuid UUID of the consumer
     * @return list of the client certificates for the given consumer.
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("{consumer_uuid}/certificates")
    @Produces(MediaType.APPLICATION_JSON)
    public List<EntitlementCertificate> getEntitlementCertificates(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("serials") String serials) {

        if (log.isDebugEnabled()) {
            log.debug("Getting client certificates for consumer: " + consumerUuid);
        }
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        poolManager.regenerateDirtyEntitlements(
            entitlementCurator.listByConsumer(consumer));

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
     * @return a File of exported certificates
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

        if (log.isDebugEnabled()) {
            log.debug("Getting client certificate zip file for consumer: " +
                consumerUuid);
        }
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        poolManager.regenerateDirtyEntitlements(
            entitlementCurator.listByConsumer(consumer));

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
            if (log.isDebugEnabled()) {
                log.debug("Requested serials: " + serials);
            }
            for (String s : serials.split(",")) {
                if (log.isDebugEnabled()) {
                    log.debug("   " + s);
                }
                serialSet.add(Long.valueOf(s));
            }
        }

        return serialSet;
    }

    private Set<String> splitKeys(String activationKeyString) {
        Set<String> keys = new HashSet<String>();
        if (activationKeyString != null && !activationKeyString.equals("")) {
            for (String s : activationKeyString.split(",")) {
                keys.add(s);
            }
        }
        return keys;
    }

    /**
     * Return the client certificate metadatthat a for the given consumer. This
     * is a small subset of data clients can use to determine which certificates
     * they need to update/fetch.
     *
     * @param consumerUuid UUID of the consumer
     * @return list of the client certificate metadata for the given consumer.
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("{consumer_uuid}/certificates/serials")
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "serials")
    public List<CertificateSerialDto> getEntitlementCertificateSerials(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        if (log.isDebugEnabled()) {
            log.debug("Getting client certificate serials for consumer: " +
                consumerUuid);
        }
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        poolManager.regenerateDirtyEntitlements(
            entitlementCurator.listByConsumer(consumer));

        List<CertificateSerialDto> allCerts = new LinkedList<CertificateSerialDto>();
        for (EntitlementCertificate cert : entCertService
            .listForConsumer(consumer)) {
            allCerts.add(new CertificateSerialDto(cert.getSerial().getId()));
        }

        return allCerts;
    }

    /**
     * Request an entitlement.
     *
     * If a pool ID is specified, we know we're binding to that exact pool. Specifying
     * an entitle date in this case makes no sense and will throw an error.
     *
     * If a list of product IDs are specified, we attempt to auto-bind to subscriptions
     * which will provide those products. An optional date can be specified allowing
     * the consumer to get compliant for some date in the future. If no date is specified
     * we assume the current date.
     *
     * If neither a pool nor an ID is specified, this is a healing request. The path
     * is similar to the bind by products, but in this case we use the installed products
     * on the consumer, and their current compliant status, to determine which product IDs
     * should be requested. The entitle date is used the same as with bind by products.
     *
     * @param consumerUuid Consumer identifier to be entitled
     * @param poolIdString Entitlement pool id.
     * @param email email address.
     * @param emailLocale locale for email address.
     * @param async True if bind should be asynchronous, defaults to false.
     * @param entitleDateStr specific date to entitle by.
     * @return Response with a list of entitlements or if async is true, a
     *         JobDetail.
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
        @QueryParam("pool") String poolIdString,
        @QueryParam("product") String[] productIds,
        @QueryParam("quantity") @DefaultValue("1") Integer quantity,
        @QueryParam("email") String email,
        @QueryParam("email_locale") String emailLocale,
        @QueryParam("async") @DefaultValue("false") boolean async,
        @QueryParam("entitle_date") String entitleDateStr) {

        // Check that only one query param was set:
        if (poolIdString != null && productIds != null && productIds.length > 0) {
            throw new BadRequestException(
                i18n.tr("Cannot bind by multiple parameters."));
        }

        if (poolIdString == null && quantity > 1) {
            throw new BadRequestException(
                i18n.tr("Cannot specify a quantity when auto-binding."));
        }

        // doesn't make sense to bind by pool and a date.
        if (poolIdString != null && entitleDateStr != null) {
            throw new BadRequestException(
                i18n.tr("Cannot bind by multiple parameters."));
        }

        // TODO: really should do this in a before we get to this call
        // so the method takes in a real Date object and not just a String.
        Date entitleDate = null;
        if (entitleDateStr != null) {
            entitleDate = ResourceDateParser.parseDateString(entitleDateStr);
        }

        // Verify consumer exists:
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        log.debug("Consumer (post verify): " + consumer);
        try {
            // I hate double negatives, but if they have accepted all
            // terms, we want comeToTerms to be true.
            if (subAdapter.hasUnacceptedSubscriptionTerms(consumer.getOwner())) {
                return Response.serverError().build();
            }
        }
        catch (CandlepinException e) {
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage());
            }
            throw e;
        }

        //
        // HANDLE ASYNC
        //
        if (async) {
            JobDetail detail = null;

            if (poolIdString != null) {
                detail = EntitlerJob.bindByPool(poolIdString, consumerUuid, quantity);
            }
            else if (productIds != null && productIds.length > 0) {
                detail = EntitlerJob.bindByProducts(productIds,
                        consumerUuid, entitleDate);
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
            try {
                entitlements = entitler.bindByProducts(productIds, consumer, entitleDate);
            }
            catch (ForbiddenException fe) {
                throw fe;
            }
            catch (CertVersionConflictException cvce) {
                throw cvce;
            }
            catch (RuntimeException re) {
                log.warn(i18n.tr("Unable to attach a subscription for a product that " +
                    "has no pool: {0} ", re.getMessage()));
            }
        }

        // Trigger events:
        entitler.sendEvents(entitlements);

        return Response.status(Response.Status.OK)
            .type(MediaType.APPLICATION_JSON).entity(entitlements).build();
    }

    /**
     * Request a list of pools and quantities that would result in an actual auto-bind.
     *
     * This is a dry run of an autobind. It allows the client to see what would be the
     * result of an autobind without executing it. It can only do this for the prevously
     * established list of installed products for the consumer
     *
     * If a service level is included in the request, then that level will override the
     * one stored on the consumer. If no service level is included then the existing
     * one will be used.
     *
     * @param consumerUuid Consumer identifier to be entitled
     * @param serviceLevel String service level override to be used for run
     * @return Response with a list of PoolQuantities containing the pool and number.
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
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        List<PoolQuantity> dryRunPools = new ArrayList<PoolQuantity>();

        try {
            checkServiceLevel(consumer.getOwner(), serviceLevel);
            dryRunPools = entitler.getDryRun(consumer, serviceLevel);
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

    private Consumer verifyAndLookupConsumer(String consumerUuid) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);

        if (consumer == null) {
            throw new NotFoundException(i18n.tr("No such consumer: {0}",
                consumerUuid));
        }
        return consumer;
    }

    private Entitlement verifyAndLookupEntitlement(String entitlementId) {
        Entitlement entitlement = entitlementCurator.find(entitlementId);

        if (entitlement == null) {
            throw new NotFoundException(i18n.tr("No such subscription: {0}",
                entitlementId));
        }
        return entitlement;
    }

    /**
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
        @Context PageRequest pageRequest) {

        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        Page<List<Entitlement>> entitlementsPage;
        if (productId != null) {
            Product p = productAdapter.getProductById(productId);
            if (p == null) {
                throw new BadRequestException(i18n.tr("No such product: {0}",
                    productId));
            }
            entitlementsPage = entitlementCurator.listByConsumerAndProduct(consumer,
                productId, pageRequest);
        }
        else {
            entitlementsPage = entitlementCurator.listByConsumer(consumer, pageRequest);
        }

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, entitlementsPage);

        List<Entitlement> returnedEntitlements = entitlementsPage.getPageData();
        poolManager.regenerateDirtyEntitlements(returnedEntitlements);

        return returnedEntitlements;
    }

    /**
     * @return an Owner
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/owner")
    public Owner getOwner(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        return consumer.getOwner();
    }

    /**
     * Unbind all entitlements.
     *
     * @param consumerUuid Unique id for the Consumer.
     * @return the total number of entitlements unbound.
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
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        if (consumer == null) {
            throw new NotFoundException(i18n.tr("Consumer with ID " +
                consumerUuid + " could not be found."));
        }

        int total = poolManager.revokeAllEntitlements(consumer);
        log.debug("Revoked " + total + " entitlements from " + consumerUuid);
        return new DeleteResult(total);

        // Need to parse off the value of subscriptionNumberArgs, probably
        // use comma separated see IntergerList in sparklines example in
        // jersey examples find all entitlements for this consumer and
        // subscription numbers delete all of those (and/or return them to
        // entitlement pool)
    }

    /**
     * Remove an entitlement by ID.
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
        @PathParam("dbid") String dbid, @Context Principal principal) {

        verifyAndLookupConsumer(consumerUuid);

        Entitlement toDelete = entitlementCurator.find(dbid);
        if (toDelete != null) {
            poolManager.revokeEntitlement(toDelete);
            return;
        }

        throw new NotFoundException(i18n.tr(
            "Entitlement with ID ''{0}'' could not be found.", dbid));
    }

    /**
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{consumer_uuid}/certificates/{serial}")
    public void unbindBySerial(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @PathParam("serial") Long serial) {

        verifyAndLookupConsumer(consumerUuid);
        Entitlement toDelete = entitlementCurator
            .findByCertificateSerial(serial);

        if (toDelete != null) {
            poolManager.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(
            i18n.tr(
                "Entitlement Certificate with serial number {0} could not be found.",
                serial.toString())); // prevent serial number formatting.
    }

    /**
     * @return a list of Event objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}/events")
    public List<Event> getConsumerEvents(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        List<Event> events = this.eventCurator.listMostRecent(FEED_LIMIT,
            consumer);
        if (events != null) {
            eventAdapter.addMessageText(events);
        }
        return events;
    }

    /**
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
            Consumer c = verifyAndLookupConsumer(consumerUuid);
            poolManager.regenerateEntitlementCertificates(c, lazyRegen);
        }
    }

    /**
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
        @PathParam("consumer_uuid")
        @Verify(value = Consumer.class, require = Access.ALL) String consumerUuid) {

        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        if (consumer.getType() == null ||
            !consumer.getType().isManifest()) {
            throw new ForbiddenException(
                i18n.tr(
                    "Consumer {0} cannot be exported. " +
                    "A manifest cannot be made for consumer of type ''{1}''.",
                    consumerUuid, consumer.getType().getLabel()));
        }

        poolManager.regenerateDirtyEntitlements(
            entitlementCurator.listByConsumer(consumer));

        File archive;
        try {
            archive = exporter.getFullExport(consumer);
            response.addHeader("Content-Disposition", "attachment; filename=" +
                archive.getName());

            sink.sendEvent(eventFactory.exportCreated(consumer));
            return archive;
        }
        catch (ExportCreationException e) {
            throw new IseException(i18n.tr("Unable to create export archive"),
                e);
        }
    }

    /**
     * Return the consumer identified by the given uuid.
     *
     * @param uuid uuid of the consumer sought.
     * @return the consumer identified by the given uuid.
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}")
    public Consumer regenerateIdentityCertificates(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid) {

        Consumer c = verifyAndLookupConsumer(uuid);

        IdentityCertificate ic = generateIdCert(c, true);
        c.setIdCert(ic);
        consumerCurator.update(c);
        Event consumerModified = this.eventFactory.consumerModified(c);
        this.sink.sendEvent(consumerModified);
        return c;
    }

    /**
     * Generates the identity certificate for the given consumer and user.
     * Throws RuntimeException if there is a problem with generating the
     * certificate.
     *
     * Regenerating an Id Cert is ok to do at any time. Since we only check
     * that the cert's date range is valid, and that it is signed by us,
     * and that the consumer UUID is in our db, it doesn't matter if the actual
     * cert itself is the one stored in our db (and therefore the most recent
     * version) or not.
     *
     * @param c Consumer whose certificate needs to be generated.
     * @param regen if true, forces a regen of the certificate.
     * @return The identity certificate for the given consumer.
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
            log.error("Problem regenerating id cert for consumer:", e);
            errored = true;
        }
        catch (IOException e) {
            log.error("Problem regenerating id cert for consumer:", e);
            errored = true;
        }

        if (errored) {
            throw new BadRequestException(i18n.tr(
                "Problem regenerating id cert for consumer {0}", c));
        }

        if (log.isDebugEnabled()) {
            log.debug("Generated identity cert: " + idCert);
            log.debug("Created consumer: " + c);
        }

        return idCert;
    }

    /**
     * @return Registered guest consumers for the given host.
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/guests")
    public List<Consumer> getGuests(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        return consumerCurator.getGuests(consumer);
    }

    /**
     * @return Registered host consumer for the given guest consumer.
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/host")
    public Consumer getHost(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        if (consumer.getFact("virt.uuid") == null ||
            consumer.getFact("virt.uuid").trim().equals("")) {
            throw new BadRequestException(i18n.tr(
                "The consumer with UUID {0} is not a virtual guest.",
                consumer.getUuid()));
        }
        return consumerCurator.getHost(consumer.getFact("virt.uuid"));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/release")
    public Release getRelease(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        if (consumer.getReleaseVer() != null) {
            return consumer.getReleaseVer();
        }
        return new Release("");
    }

    /**
     * Return the compliance status of the specified consumer.
     *
     * @param uuid uuid of the consumer to get status for.
     * @return the compliance status by the given uuid.
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}/compliance")
    @Transactional
    public ComplianceStatus getComplianceStatus(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid) {
        Consumer consumer = verifyAndLookupConsumer(uuid);
        ComplianceStatus status = this.complianceRules.getStatus(consumer,
            Calendar.getInstance().getTime());

        // NOTE: If this method ever changes to accept an optional date, do not update this
        // field on the consumer if the date is specified:
        consumer.setEntitlementStatus(status.getStatus());

        return status;
    }

    private void addDataToInstalledProducts(Consumer consumer) {

        ComplianceStatus complianceStatus = complianceRules.getStatus(
                           consumer, Calendar.getInstance().getTime());

        ConsumerInstalledProductEnricher enricher = new ConsumerInstalledProductEnricher(
            consumer, complianceStatus, complianceRules);

        for (ConsumerInstalledProduct cip : consumer.getInstalledProducts()) {
            String prodId = cip.getProductId();
            Product prod = productAdapter.getProductById(prodId);
            if (prod != null) {
                enricher.enrich(cip, prod);
            }
        }
    }
    /*
     *
     * Allows the superadmin to remove a deletion record for a consumer. The
     * main use case for this would be if a user accidently deleted a non-RHEL
     * hypervisor, causing it to no longer be auto-detected via virt-who.
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
            throw new NotFoundException("Deletion record for hypervisor " +
                            uuid + " not found.");
        }
        deletedConsumerCurator.delete(dc);
    }
}
