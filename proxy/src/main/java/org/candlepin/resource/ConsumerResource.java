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
package org.candlepin.resource;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

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
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EventCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.User;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.consumer.ConsumerDeleteHelper;
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
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;

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

/**
 * API Gateway for Consumers
 */
@Path("/consumers")
public class ConsumerResource {
    private static final Pattern CONSUMER_NAME_PATTERN = Pattern
        .compile("[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");

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
    private PoolCurator poolCurator;
    private ConsumerRules consumerRules;
    private ConsumerDeleteHelper consumerDeleteHelper;
    private OwnerCurator ownerCurator;
    private ActivationKeyCurator activationKeyCurator;
    private Entitler entitler;
    private ComplianceRules complianceRules;

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
        Exporter exporter, PoolManager poolManager, PoolCurator poolCurator,
        ConsumerRules consumerRules, ConsumerDeleteHelper consumerDeleteHelper,
        OwnerCurator ownerCurator, ActivationKeyCurator activationKeyCurator,
        Entitler entitler, ComplianceRules complianceRules) {

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
        this.poolCurator = poolCurator;
        this.consumerRules = consumerRules;
        this.consumerDeleteHelper = consumerDeleteHelper;
        this.ownerCurator = ownerCurator;
        this.eventAdapter = eventAdapter;
        this.activationKeyCurator = activationKeyCurator;
        this.entitler = entitler;
        this.complianceRules = complianceRules;
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
    public List<Consumer> list(@QueryParam("username") String userName,
        @QueryParam("type") String typeLabel,
        @QueryParam("owner") String ownerKey) {
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
        return consumerCurator.listByUsernameAndType(userName, type, owner);
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

        if (!isConsumerNameValid(consumer.getName())) {
            throw new BadRequestException(
                i18n.tr("System name cannot contain most special characters."));
        }

        if (consumer.getName().indexOf('#') == 0) {
            // this is a bouncycastle restriction
            throw new BadRequestException(
                i18n.tr("System name cannot begin with # character"));
        }

        if (userName == null) {
            userName = principal.getUsername();
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

        ConsumerType type = lookupConsumerType(consumer.getType().getLabel());
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            if (keys.size() > 0) {
                throw new BadRequestException(
                    i18n.tr("A consumer type of 'person' cannot be" +
                        " used with activation keys"));
            }
            verifyPersonConsumer(consumer, type, owner, userName);
        }

        if (userName != null) {
            consumer.setUsername(userName);
        }
        consumer.setOwner(owner);
        consumer.setType(type);
        consumer.setCanActivate(subAdapter.canActivateSubscription(consumer));
        consumer.setAutoheal(true); // this is the default
        if (consumer.getServiceLevel() == null) {
            consumer.setServiceLevel("");
        }

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

            // TODO: Process activation keys.
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

    private void checkServiceLevel(Owner owner, String serviceLevel)
        throws BadRequestException {
        if (serviceLevel != null &&
            !serviceLevel.trim().equals("")) {
            if (!poolCurator.retrieveServiceLevelsForOwner(owner)
                 .contains(serviceLevel)) {
                throw new BadRequestException(
                    i18n.tr("Cannot set a service level for a consumer " +
                            "that is not available to its organization."));
            }
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

        // When registering person consumers we need to be sure the username
        // has some association with the owner the consumer is destined for:
        if (!user.hasOwnerAccess(owner, Access.ALL) && !user.isSuperAdmin()) {
            throw new ForbiddenException(i18n.tr(
                "User {0} has no roles for organization {1}",
                user.getUsername(), owner.getKey()));
        }

        // TODO: Refactor out type specific checks?
        if (type.isType(ConsumerTypeEnum.PERSON) && user != null) {
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

    private boolean isConsumerNameValid(String name) {
        if (name == null) {
            return false;
        }

        return CONSUMER_NAME_PATTERN.matcher(name).matches();
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
                poolManager.refreshPools(existingOwner);
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

        performConsumerUpdates(consumer, toUpdate);
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

        boolean changesMade = checkForFactsUpdate(toUpdate, updated);
        changesMade = checkForInstalledProductsUpdate(toUpdate, updated) || changesMade;
        changesMade = checkForGuestsUpdate(toUpdate, updated) || changesMade;

        // Allow optional setting of the autoheal attribute:
        if (updated.isAutoheal() != null &&
            toUpdate.isAutoheal() != updated.isAutoheal()) {
            if (log.isDebugEnabled()) {
                log.debug("   Updating consumer autoheal setting.");
            }
            toUpdate.setAutoheal(updated.isAutoheal());
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
            Consumer guest = consumerCurator.findByVirtUuid(guestId.getGuestId());

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

                revokeGuestEntitlementsMatchingHost(host, guest);
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
        }

        // Check guests that have been removed.
        for (GuestId guestId : removedGuests) {
            // Report that the guestId was removed.
            if (log.isDebugEnabled()) {
                log.debug("Guest ID removed: " + guestId.getGuestId());
            }
            sink.sendEvent(eventFactory.guestIdDeleted(existing, guestId));

        }
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

    private void revokeGuestEntitlementsMatchingHost(Consumer host, Consumer guest) {
        // we need to create a list of entitlements to delete before actually
        // deleting, otherwise we are tampering with the loop iterator (BZ #786730)
        Set<Entitlement> deletableGuestEntitlements = new HashSet<Entitlement>();
        for (Entitlement entitlement : guest.getEntitlements()) {
            Pool pool = entitlement.getPool();
            String requiredHost = getRequiredHost(pool);
            if (isVirtOnly(pool) && requiredHost.equals(host.getUuid())) {
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
        consumerRules.onConsumerDelete(consumerDeleteHelper, toDelete);

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
                i18n.tr("Unable to create entitlement archive"), e);
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
            catch (RuntimeException re) {
                log.warn(i18n.tr("Asked to be subscribed to a product that " +
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
            dryRunPools = entitler.getDryRunMap(consumer, serviceLevel);
        }
        catch (ForbiddenException fe) {
            throw fe;
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
            throw new NotFoundException(i18n.tr("No such entitlement: {0}",
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
    public List<Entitlement> listEntitlements(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("product") String productId) {

        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        if (productId != null) {
            Product p = productAdapter.getProductById(productId);
            if (p == null) {
                throw new BadRequestException(i18n.tr("No such product: {0}",
                    productId));
            }
            return entitlementCurator.listByConsumerAndProduct(consumer,
                productId);
        }

        return entitlementCurator.listByConsumer(consumer);

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
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{consumer_uuid}/entitlements")
    public void unbindAll(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        // FIXME: just a stub, needs CertifcateService (and/or a
        // CertificateCurator) to lookup by serialNumber
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        if (consumer == null) {
            throw new NotFoundException(i18n.tr("Consumer with ID " +
                consumerUuid + " could not be found."));
        }

        poolManager.revokeAllEntitlements(consumer);

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
        @QueryParam("entitlement") String entitlementId) {
        if (entitlementId != null) {
            Entitlement e = verifyAndLookupEntitlement(entitlementId);
            poolManager.regenerateCertificatesOf(e, false);
        }
        else {
            Consumer c = verifyAndLookupConsumer(consumerUuid);
            poolManager.regenerateEntitlementCertificates(c);
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
        if (!consumer.getType().isManifest()) {
            throw new ForbiddenException(
                i18n.tr(
                    "Consumer {0} cannot be exported. " +
                    "A manifest cannot be made for consumer of type ''{1}''.",
                    consumerUuid, consumer.getType().getLabel()));
        }

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

        try {
            IdentityCertificate ic = generateIdCert(c, true);
            c.setIdCert(ic);
            consumerCurator.update(c);
            Event consumerModified = this.eventFactory.consumerModified(c);
            this.sink.sendEvent(consumerModified);
            return c;
        }
        catch (Exception e) {
            log.error("Problem regenerating id cert for consumer:", e);
            throw new BadRequestException(i18n.tr(
                "Problem regenerating id cert for consumer {0}", c));
        }
    }

    /**
     * Generates the identity certificate for the given consumer and user.
     * Throws RuntimeException if there is a problem with generating the
     * certificate.
     *
     * @param c Consumer whose certificate needs to be generated.
     * @param regen if true, forces a regen of the certificate.
     * @return The identity certificate for the given consumer.
     * @throws IOException thrown if there's a problem generating the cert.
     * @throws GeneralSecurityException thrown incase of security error.
     */
    private IdentityCertificate generateIdCert(Consumer c, boolean regen)
        throws GeneralSecurityException, IOException {

        IdentityCertificate idCert = null;

        if (regen) {
            idCert = identityCertService.regenerateIdentityCert(c);
        }
        else {
            idCert = identityCertService.generateIdentityCert(c);
        }

        if (log.isDebugEnabled()) {
            log.debug("Generated identity cert: " + idCert);
            log.debug("Created consumer: " + c);
        }

        if (idCert == null) {
            throw new RuntimeException("Error generating identity certificate.");
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
}
