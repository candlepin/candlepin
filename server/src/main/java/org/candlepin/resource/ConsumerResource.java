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
import org.candlepin.auth.Verify;
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
import org.candlepin.controller.AutobindDisabledForOwnerException;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerialDto;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentAccessCertificate;
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
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Release;
import org.candlepin.model.User;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.PoolIdAndQuantity;
import org.candlepin.pinsetter.tasks.EntitleByProductsJob;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.dto.ContentAccessListing;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.EntitlementFinderUtil;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.resteasy.DateFormat;
import org.candlepin.resteasy.parameter.CandlepinParam;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.ContentAccessCertServiceAdapter;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.sync.ExportCreationException;
import org.candlepin.util.FactValidator;
import org.candlepin.util.PropertyValidationException;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
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
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

/**
 * API Gateway for Consumers
 */
@Path("/consumers")
@Api(value = "consumers", authorizations = { @Authorization("basic") })
public class ConsumerResource {
    private Pattern consumerSystemNamePattern;
    private Pattern consumerPersonNamePattern;

    private static Logger log = LoggerFactory.getLogger(ConsumerResource.class);
    private static final int FEED_LIMIT = 1000;

    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private OwnerProductCurator ownerProductCurator;
    private SubscriptionServiceAdapter subAdapter;
    private OwnerServiceAdapter ownerAdapter;
    private EntitlementCurator entitlementCurator;
    private IdentityCertServiceAdapter identityCertService;
    private EntitlementCertServiceAdapter entCertService;
    private ContentAccessCertServiceAdapter contentAccessCertService;
    private UserServiceAdapter userService;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private EventCurator eventCurator;
    private EventAdapter eventAdapter;
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
    private ManifestManager manifestManager;
    private FactValidator factValidator;
    private ConsumerTypeValidator consumerTypeValidator;
    private ConsumerEnricher consumerEnricher;

    @Inject
    @SuppressWarnings({"checkstyle:parameternumber"})
    public ConsumerResource(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        OwnerProductCurator ownerProductCurator,
        SubscriptionServiceAdapter subAdapter,
        OwnerServiceAdapter ownerAdapter,
        EntitlementCurator entitlementCurator,
        IdentityCertServiceAdapter identityCertService,
        EntitlementCertServiceAdapter entCertServiceAdapter, I18n i18n,
        EventSink sink, EventFactory eventFactory, EventCurator eventCurator,
        EventAdapter eventAdapter, UserServiceAdapter userService, PoolManager poolManager,
        ConsumerRules consumerRules, OwnerCurator ownerCurator,
        ActivationKeyCurator activationKeyCurator, Entitler entitler,
        ComplianceRules complianceRules, DeletedConsumerCurator deletedConsumerCurator,
        EnvironmentCurator environmentCurator,
        DistributorVersionCurator distributorVersionCurator,
        Configuration config, ContentCurator contentCurator,
        CdnCurator cdnCurator, CalculatedAttributesUtil calculatedAttributesUtil,
        ConsumerBindUtil consumerBindUtil,
        ManifestManager manifestManager,
        ContentAccessCertServiceAdapter contentAccessCertService,
        FactValidator factValidator,
        ConsumerTypeValidator consumerTypeValidator,
        ConsumerEnricher consumerEnricher) {

        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.subAdapter = subAdapter;
        this.ownerAdapter = ownerAdapter;
        this.entitlementCurator = entitlementCurator;
        this.identityCertService = identityCertService;
        this.entCertService = entCertServiceAdapter;
        this.i18n = i18n;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.eventCurator = eventCurator;
        this.userService = userService;
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
        this.manifestManager = manifestManager;
        this.contentAccessCertService = contentAccessCertService;
        this.factValidator = factValidator;
        this.consumerTypeValidator = consumerTypeValidator;
        this.consumerEnricher = consumerEnricher;
    }

    /**
     * Sanitizes inbound consumer facts, truncating long facts and dropping invalid or untracked
     * facts. The consumer will be updated in-place.
     *
     * @param consumer
     *  The consumer containing the facts to sanitize
     */
    private void sanitizeConsumerFacts(Consumer consumer) {
        if (consumer != null) {
            Map<String, String> facts = consumer.getFacts();

            if (facts != null && facts.size() > 0) {
                log.info("Sanitizing facts for consumer {}", consumer.getName());
                Map<String, String> sanitized = new HashMap<String, String>();
                Set<String> lowerCaseKeys = new HashSet<String>();

                String factPattern = config.getString(ConfigProperties.CONSUMER_FACTS_MATCHER);
                Pattern pattern = Pattern.compile(factPattern);

                for (Map.Entry<String, String> fact : facts.entrySet()) {
                    String key = fact.getKey();
                    String value = fact.getValue();

                    // Check for null fact keys (discard and continue)
                    if (key == null) {
                        log.warn("  Consumer contains a fact using a null key. Discarding fact...");
                        continue;
                    }

                    // facts are case insensitive
                    String lowerCaseKey = key.toLowerCase();
                    if (lowerCaseKeys.contains(lowerCaseKey)) {
                        log.warn("  Consumer contains duplicate fact. Discarding fact \"{}\"" +
                            " with value \"{}\"", key, value);
                        continue;
                    }

                    // Check for fact match (discard and continue)
                    if (!pattern.matcher(key).matches()) {
                        log.warn("  Consumer fact \"{}\" does not match pattern \"{}\"", key, factPattern);
                        log.warn("  Discarding fact \"{}\"...", key);
                        continue;
                    }

                    // Check for long keys or values, truncating as necessary
                    if (key.length() > FactValidator.FACT_MAX_LENGTH) {
                        key = key.substring(0, FactValidator.FACT_MAX_LENGTH - 3) + "...";
                    }

                    if (value != null && value.length() > FactValidator.FACT_MAX_LENGTH) {
                        value = value.substring(0, FactValidator.FACT_MAX_LENGTH - 3) + "...";
                    }

                    // Validate fact (discarding malformed facts) (discard and continue)
                    try {
                        this.factValidator.validate(key, value);
                    }
                    catch (PropertyValidationException e) {
                        log.warn("  {}", e.getMessage());
                        log.warn("  Discarding fact \"{}\"...", key);
                        continue;
                    }

                    sanitized.put(key, value);
                    lowerCaseKeys.add(lowerCaseKey);
                }

                consumer.setFacts(sanitized);
            }
        }
    }

    @ApiOperation(notes = "Retrieves a list of the Consumers", value = "list", response = Consumer.class,
        responseContainer = "list")
    @ApiResponses({ @ApiResponse(code =  400, message = ""), @ApiResponse(code =  404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "consumers")
    @Paginate
    @SuppressWarnings("checkstyle:indentation")
    public CandlepinQuery<Consumer> list(@QueryParam("username") String userName,
        @QueryParam("type") Set<String> typeLabels,
        @QueryParam("owner") String ownerKey,
        @QueryParam("uuid") List<String> uuids,
        @QueryParam("hypervisor_id") List<String> hypervisorIds,
        @QueryParam("fact") @CandlepinParam(type = KeyValueParameter.class)
            List<KeyValueParameter> attrFilters,
        @Context PageRequest pageRequest) {

        if (userName == null && (typeLabels == null || typeLabels.isEmpty()) && ownerKey == null &&
            (uuids == null || uuids.isEmpty()) && (hypervisorIds == null || hypervisorIds.isEmpty()) &&
            (attrFilters == null || attrFilters.isEmpty())) {
            throw new BadRequestException(i18n.tr("Must specify at least one search criteria."));
        }

        Owner owner = null;
        if (ownerKey != null) {
            owner = ownerCurator.lookupByKey(ownerKey);
            if (owner == null) {
                throw new NotFoundException(i18n.tr(
                    "owner with key: {0} was not found.", ownerKey));
            }
        }

        List<ConsumerType> types =  consumerTypeValidator.findAndValidateTypeLabels(typeLabels);

        return this.consumerCurator.searchOwnerConsumers(
            owner, userName, types, uuids, hypervisorIds, attrFilters,
            Collections.<String>emptyList(), Collections.<String>emptyList(),
            Collections.<String>emptyList());
    }

    @ApiOperation(
        notes = "Checks for the existence of a Consumer. This method is used to check if a consumer" +
        " is available on a particular shard.  There is no need to do a full " +
        "GET for the consumer for this check.",
        value = "")
    @ApiResponses({ @ApiResponse(code = 404, message = "If the consumer doesn't exist or cannot be accessed"),
        @ApiResponse(code = 204, message = "If the consumer exists and can be accessed") })
    @HEAD
    @Produces(MediaType.WILDCARD)
    @Path("{consumer_uuid}/exists")
    public void consumerExists(
        @PathParam("consumer_uuid") String uuid) {
        if (!consumerCurator.doesConsumerExist(uuid)) {
            throw new NotFoundException(i18n.tr("Consumer with id {0} could not be found.", uuid));
        }
    }

    @ApiOperation(notes = "Retrieves a single Consumer", value = "getConsumer")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
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
                int days = config.getInt(ConfigProperties.IDENTITY_CERT_EXPIRY_THRESHOLD, 90);
                Date futureExpire = Util.addDaysToDt(days);
                // if expiration is within 90 days, regenerate it
                log.debug("Threshold [{}] expires on [{}] futureExpire [{}]", days, expire, futureExpire);

                if (expire.before(futureExpire)) {
                    log.info("Regenerating identity certificate for consumer: {}, expiry: {}", uuid, expire);
                    consumer = this.regenerateIdentityCertificate(consumer);
                }
            }

            // enrich with subscription data
            consumer.setCanActivate(subAdapter.canActivateSubscription(consumer));

            // enrich with installed product data
            this.consumerEnricher.enrich(consumer);
        }

        return consumer;
    }

    @ApiOperation(notes = "Creates a Consumer. NOTE: Opening this method up " +
        "to everyone, as we have nothing we can reliably " +
        "verify in the method signature. Instead we have to " +
        "figure out what owner this consumer is destined for " +
        "(due to backward compatability with existing clients " +
        "which do not specify an owner during registration), " +
        "and then check the access to the specified owner in " + "the method itself.", value = "create")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 403, message = ""),
        @ApiResponse(code = 404, message = "") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole(noAuth = true)
    @Transactional
    public Consumer create(
        @ApiParam(name = "consumer", required = true) Consumer consumer,
        @Context Principal principal,
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

        validateOnKeyStrings(keyStrings, ownerKey, userName);

        Owner owner = setupOwner(principal, ownerKey);
        // Raise an exception if none of the keys specified exist for this owner.
        List<ActivationKey> keys = checkActivationKeys(principal, owner, keyStrings);

        userName = setUserName(consumer, principal, userName);
        checkConsumerName(consumer);

        ConsumerType type = lookupConsumerType(consumer.getType().getLabel());
        validateViaConsumerType(consumer, type, keys, owner, userName, principal);

        if (consumer.isShare()) {
            // Share consumers do not need identity certificates so refuse to create them.
            identityCertCreation = false;
            validateShareConsumer(consumer, principal, keys);
            consumer.setAutoheal(false);

        }
        else {
            consumer.setCanActivate(subAdapter.canActivateSubscription(consumer));
            consumer.setAutoheal(true); // this is the default
        }

        consumer.setOwner(owner);
        consumer.setType(type);

        if (consumer.getServiceLevel() == null) {
            consumer.setServiceLevel("");
        }

        // Sanitize the inbound facts
        this.sanitizeConsumerFacts(consumer);

        // If no service level was specified, and the owner has a default set, use it:
        if (consumer.getServiceLevel().equals("") && owner.getDefaultServiceLevel() != null &&
            !consumer.isShare()) {

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

        HypervisorId hvsrId = consumer.getHypervisorId();
        if (hvsrId != null && hvsrId.getHypervisorId() != null && !hvsrId.getHypervisorId().isEmpty()) {
            // If a hypervisorId is supplied, make sure the consumer and owner are correct
            hvsrId.setConsumer(consumer);
        }

        validateContentAccessMode(consumer);
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
                log.info("Creating with specific last check-in time: {}", consumer.getLastCheckin());
                consumer.setLastCheckin(lastCheckIn);
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

            log.info("Consumer {} created in org {}", consumer.getUuid(), consumer.getOwner().getKey());

            return consumer;
        }
        catch (CandlepinException ce) {
            // If it is one of ours, rethrow it.
            throw ce;
        }
        catch (AutobindDisabledForOwnerException e) {
            throw new BadRequestException(i18n.tr("Could not register unit with key enabling auto-attach. " +
                "Auto-attach is disabled for org ''{0}''.", consumer.getOwner().getKey()));
        }
        catch (Exception e) {
            log.error("Problem creating unit:", e);
            throw new BadRequestException(i18n.tr("Problem creating unit {0}", consumer));
        }
    }

    /**
     * Validate any updates performed on a share consumer
     * @param oldShare the consumer being updated
     * @param newShare the consumer posted to the resource method
     * @param principal the principal performing the operation
     * @throws BadRequestException if any validations fail
     */
    private void validateShareConsumerUpdate(Consumer oldShare, Consumer newShare, Principal principal)
        throws BadRequestException {
        String oldRecipient = oldShare.getRecipientOwnerKey();
        String newRecipient = newShare.getRecipientOwnerKey();

        if (oldRecipient == null) {
            throw new BadRequestException(
                i18n.tr("A consumer cannot be converted to a share type once it has been created"));
        }

        if (newRecipient == null) {
            throw new BadRequestException(
                i18n.tr("A share consumer cannot be converted to a non share type once it has been created"));
        }

        if (!oldRecipient.equals(newRecipient)) {
            throw new BadRequestException(
                i18n.tr("The share recipient cannot be modified once the share has been created"));
        }
        validateShareConsumer(newShare, principal, Collections.<ActivationKey>emptyList());
    }
    /**
     * Ensure that certain fields remain unset when creating a share consumer.
     * @param consumer the consumer to validate
     * @param principal the principal performing the operation
     * @param keys any provided activation keys
     * @throws BadRequestException if any validations fail
     */
    private void validateShareConsumer(Consumer consumer, Principal principal, List<ActivationKey> keys)
        throws BadRequestException {
        if (keys.size() > 0) {
            throw new BadRequestException(
                i18n.tr("A unit type of \"share\" cannot be used with activation keys"));
        }
        if (StringUtils.isNotBlank(consumer.getServiceLevel())) {
            throw new BadRequestException(i18n.tr("A unit type of \"share\" cannot have a service level"));
        }
        if (!consumer.getReleaseVer().equals(new Release(null))) {
            throw new BadRequestException(i18n.tr("A unit type of \"share\" cannot have a release version"));
        }
        if (!consumer.getInstalledProducts().isEmpty()) {
            throw new BadRequestException(i18n.tr("A unit type of \"share\" cannot have installed products"));
        }
        if (StringUtils.isNotBlank(consumer.getContentAccessMode())) {
            throw new BadRequestException(
                i18n.tr("A unit type of \"share\" cannot have a content access mode"));
        }
        if (consumer.getGuestIds() != null && !consumer.getGuestIds().isEmpty()) {
            throw new BadRequestException(i18n.tr("A unit type of \"share\" cannot have guest IDs"));
        }
        if (consumer.getHypervisorId() != null) {
            throw new BadRequestException(i18n.tr("A unit type of \"share\" cannot have a hypervisor ID"));
        }
        if (consumer.getRecipientOwnerKey() == null) {
            throw new BadRequestException(
                i18n.tr("A unit type of \"share\" must specify a recipient org key"));
        }
        if (consumer.isGuest()) {
            throw new BadRequestException(i18n.tr("A unit type of \"share\" cannot be a virtual guest"));
        }

        String recipient = consumer.getRecipientOwnerKey();
        Owner recipientOwner = ownerCurator.lookupByKey(recipient);
        if (recipientOwner == null) {
            throw new NotFoundException(i18n.tr("owner with key: {0} was not found.", recipient));
        }
        // Check permissions for current principal on the recipient owner
        if (!principal.canAccess(recipientOwner, SubResource.ENTITLEMENTS, Access.CREATE)) {
            log.warn("User {} does not have access to create shares to org {}", principal
                .getPrincipalName(), recipient);
            throw new NotFoundException(i18n.tr("owner with key: {0} was not found.", recipient));
        }
    }

    private void validateContentAccessMode(Consumer consumer) throws BadRequestException {
        if (consumer.getContentAccessMode() != null &&
            !consumer.getOwner().isAllowedContentAccessMode(consumer.getContentAccessMode())) {
            throw new BadRequestException(i18n.tr(
                "The consumer cannot use the supplied content access mode."));
        }
        if (consumer.getContentAccessMode() != null && !consumer.isManifestDistributor()) {
            throw new BadRequestException(i18n.tr(
                "The consumer cannot be assigned a content access mode."));
        }
    }

    private void validateOnKeyStrings(Set<String> keyStrings, String ownerKey, String userName) {
        if (!keyStrings.isEmpty()) {
            if (ownerKey == null) {
                throw new BadRequestException(i18n.tr("Org required to register with activation keys."));
            }
            if (userName != null) {
                throw new BadRequestException(i18n.tr("Cannot specify username with activation keys."));
            }
        }
    }

    private void validateViaConsumerType(Consumer consumer, ConsumerType type, List<ActivationKey> keys,
        Owner owner, String userName, Principal principal) {
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            if (keys.size() > 0) {
                throw new BadRequestException(
                        i18n.tr("A unit type of ''person'' cannot be used with activation keys"));
            }

            if (!isConsumerPersonNameValid(consumer.getName())) {
                throw new BadRequestException(i18n.tr("System name cannot contain most special characters."));
            }

            verifyPersonConsumer(consumer, type, owner, userName, principal);
        }

        if (type.isType(ConsumerTypeEnum.SYSTEM) && !isConsumerSystemNameValid(consumer.getName())) {
            throw new BadRequestException(i18n.tr("System name cannot contain most special characters."));
        }

        HttpRequest httpRequest = ResteasyProviderFactory.getContextData(HttpRequest.class);
        if (httpRequest != null) {
            List<String> userAgent = httpRequest.getHttpHeaders().getRequestHeader("user-agent");
            if (type.isManifest() && userAgent != null &&
                userAgent.size() > 0 && userAgent.get(0).startsWith("RHSM")) {
                throw new BadRequestException(
                        i18n.tr("You may not create a manifest consumer via Subscription Manager."));
            }
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
            throw new BadRequestException(
                i18n.tr("None of the activation keys specified exist for this org."));
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
                        ConsumerCapability cc = new ConsumerCapability(existing, dvc.getName());
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
                if (!update.getCapabilities().equals(existing.getCapabilities())) {
                    existing.setCapabilities(update.getCapabilities());
                    change = true;
                }
            }
            else if (update.getFact("distributor_version") !=  null) {
                DistributorVersion dv = distributorVersionCurator.findByName(
                    update.getFact("distributor_version"));

                if (dv != null) {
                    Set<ConsumerCapability> ccaps = new HashSet<ConsumerCapability>();
                    for (DistributorVersionCapability dvc : dv.getCapabilities()) {
                        ConsumerCapability cc = new ConsumerCapability(existing, dvc.getName());
                        ccaps.add(cc);
                    }

                    existing.setCapabilities(ccaps);
                }

                change = true;
            }
        }
        if (change) {
            log.debug("Capabilities changed.");
        }
        else {
            log.debug("Capability list either null or does not contain changes.");
        }
        return change;
    }

    /**
     * @param consumer
     * @return a String object
     */
    private void checkConsumerName(Consumer consumer) {
        // for now this applies to both types consumer
        if (consumer.getName() != null) {
            if (consumer.getName().indexOf('#') == 0) {
                // this is a bouncycastle restriction
                throw new BadRequestException(i18n.tr("System name cannot begin with # character"));
            }

            int max = Consumer.MAX_LENGTH_OF_CONSUMER_NAME;
            if (consumer.getName().length() > max) {
                String m = "Name of the consumer should be shorter than {0} characters.";
                throw new BadRequestException(i18n.tr(m, Integer.toString(max + 1)));
            }
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
            throw new NotFoundException(i18n.tr("Activation key ''{0}'' not found for organization ''{1}''.",
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
            log.warn("User service does not allow user lookups, cannot verify person consumer.");
        }

        if (user == null) {
            throw new NotFoundException(i18n.tr("User with ID ''{0}'' could not be found."));
        }

        // When registering person consumers we need to be sure the username
        // has some association with the owner the consumer is destined for:
        if (!principal.canAccess(owner, SubResource.NONE, Access.ALL) && !principal.hasFullAccess()) {
            throw new ForbiddenException(i18n.tr("User ''{0}'' has no roles for organization ''{1}''",
                user.getUsername(), owner.getKey()));
        }

        // TODO: Refactor out type specific checks?
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            Consumer existing = consumerCurator.findByUser(user);

            if (existing != null && existing.getType().isType(ConsumerTypeEnum.PERSON)) {
                // TODO: This is not the correct error code for this situation!
                throw new BadRequestException(
                    i18n.tr("User ''{0}'' has already registered a personal consumer", user.getUsername()));
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
                throw new BadRequestException(i18n.tr("You must specify an organization for new units."));
            }

            ownerKey = ownerKeys.get(0);
        }

        createOwnerIfNeeded(principal);

        Owner owner = ownerCurator.lookupByKey(ownerKey);
        if (owner == null) {
            throw new BadRequestException(i18n.tr("Organization {0} does not exist.", ownerKey));
        }

        // Check permissions for current principal on the owner:
        if ((principal instanceof UserPrincipal) &&
            !principal.canAccess(owner, SubResource.CONSUMERS, Access.CREATE)) {

            log.warn("User {} does not have access to create consumers in org {}",
                principal.getPrincipalName(), owner.getKey());

            throw new NotFoundException(i18n.tr("owner with key: {0} was not found.", owner.getKey()));
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
            // If this isn't a user principal we can't check for owners that may need to be created.
            return;
        }

        for (Owner owner : ((UserPrincipal) principal).getOwners()) {
            Owner existingOwner = ownerCurator.lookupByKey(owner.getKey());

            if (existingOwner == null) {
                log.info("Principal carries permission for owner that does not exist.");
                log.info("Creating new owner: {}", owner.getKey());

                existingOwner = ownerCurator.create(owner);

                poolManager.getRefresher(this.subAdapter, this.ownerAdapter)
                    .add(existingOwner)
                    .run();
            }
        }
    }

    private ConsumerType lookupConsumerType(String label) {
        ConsumerType type = consumerTypeCurator.lookupByLabel(label);

        if (type == null) {
            throw new BadRequestException(i18n.tr("Unit type ''{0}'' could not be found.", label));
        }

        return type;
    }


    // While this is a PUT, we are treating it as a PATCH until this operation
    // becomes more prevalent. We only update the portions of the consumer that appear
    // to be set.
    @ApiOperation(notes = "Updates a Consumer", value = "updateConsumer")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}")
    @Transactional
    public void updateConsumer(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid,
        @ApiParam(name = "consumer", required = true) Consumer consumer,
        @Context Principal principal) {

        Consumer toUpdate = consumerCurator.verifyAndLookupConsumer(uuid);

        VirtConsumerMap guestConsumerMap = new VirtConsumerMap();
        if (consumer.getGuestIds() != null) {
            Set<String> allGuestIds = new HashSet<String>();
            for (GuestId gid : consumer.getGuestIds()) {
                allGuestIds.add(gid.getGuestId());
            }

            guestConsumerMap = consumerCurator.getGuestConsumersMap(toUpdate.getOwner(), allGuestIds);
        }

        // Sanitize the inbound facts before applying the update
        this.sanitizeConsumerFacts(consumer);

        if (toUpdate.isShare() || consumer.isShare()) {
            validateShareConsumerUpdate(toUpdate, consumer, principal);
        }

        if (performConsumerUpdates(consumer, toUpdate, guestConsumerMap)) {
            try {
                consumerCurator.update(toUpdate);
            }
            catch (CandlepinException ce) {
                // If it is one of ours, rethrow it.
                throw ce;
            }
            catch (Exception e) {
                log.error("Problem updating unit:", e);
                throw new BadRequestException(i18n.tr("Problem updating unit {0}", consumer));
            }
        }
    }

    public boolean performConsumerUpdates(Consumer updated, Consumer toUpdate,
        VirtConsumerMap guestConsumerMap) {

        return performConsumerUpdates(updated, toUpdate, guestConsumerMap, true);
    }

    @Transactional
    public boolean performConsumerUpdates(Consumer updated, Consumer toUpdate,
        VirtConsumerMap guestConsumerMap, boolean isIdCert) {

        log.debug("Updating consumer: {}", toUpdate.getUuid());

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
        changesMade = checkForGuestsUpdate(toUpdate, updated, guestConsumerMap) || changesMade;
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

        String environmentId = updated.getEnvironment() == null ? null : updated.getEnvironment().getId();
        if (environmentId != null && (toUpdate.getEnvironment() == null ||
            !toUpdate.getEnvironment().getId().equals(environmentId))) {
            Environment e = environmentCurator.find(environmentId);
            if (e == null) {
                throw new NotFoundException(i18n.tr(
                    "Environment with ID ''{0}'' could not be found.", environmentId));
            }
            log.info("Updating environment to: {}", environmentId);
            toUpdate.setEnvironment(e);

            // lazily regenerate certs, so the client can still work
            poolManager.regenerateCertificatesOf(toUpdate, true);
            changesMade = true;
        }

        // like the other values in an update, if consumer name is null, act as if
        // it should remain the same
        if (updated.getName() != null && !toUpdate.getName().equals(updated.getName())) {
            checkConsumerName(updated);

            log.info("Updating consumer name: {} -> {}", toUpdate.getName(), updated.getName());
            toUpdate.setName(updated.getName());

            // get the new name into the id cert if we are using the cert
            if (isIdCert) {
                IdentityCertificate ic = generateIdCert(toUpdate, true);
                toUpdate.setIdCert(ic);
            }
        }

        if (updated.getContentAccessMode() != null &&
            !updated.getContentAccessMode().equals(toUpdate.getContentAccessMode()) &&
            toUpdate.isManifestDistributor()) {
            if (!toUpdate.getOwner().isAllowedContentAccessMode(updated.getContentAccessMode())) {
                throw new BadRequestException(i18n.tr(
                    "The consumer cannot use the supplied content access mode."));
            }
            toUpdate.setContentAccessMode(updated.getContentAccessMode());
            changesMade = true;
        }
        if (!StringUtils.isEmpty(updated.getContentAccessMode()) && !toUpdate.isManifestDistributor()) {
            throw new BadRequestException(i18n.tr("The consumer cannot be assigned a content access mode."));
        }

        if (updated.getLastCheckin() != null) {
            log.info("Updating to specific last checkin time: {}", updated.getLastCheckin());
            toUpdate.setLastCheckin(updated.getLastCheckin());
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
            if (incomingId.getHypervisorId() == null || incomingId.getHypervisorId().isEmpty()) {
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
                    existing.setHypervisorId(new HypervisorId(incomingId.getHypervisorId()));
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
     * <p></p>
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
     * Check if the host consumers guest IDs have changed. If they do not appear to
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
        VirtConsumerMap guestConsumerMap) {

        if (incoming.getGuestIds() == null) {
            log.debug("Guests not included in this consumer update, skipping update.");
            return false;
        }

        log.debug("Updating {} guest IDs.", incoming.getGuestIds().size());
        List<GuestId> removedGuests = getRemovedGuestIds(existing, incoming);
        List<GuestId> addedGuests = getAddedGuestIds(existing, incoming);

        List<GuestId> existingGuests = existing.getGuestIds();

        // remove guests that are missing.
        if (existingGuests != null) {
            log.debug("removing IDs.");
            for (GuestId guestId : removedGuests) {
                existingGuests.remove(guestId);
                if (log.isDebugEnabled()) {
                    log.debug("Guest ID removed: {}", guestId);
                }
                sink.queueEvent(eventFactory.guestIdDeleted(guestId));
            }
        }
        // Check guests that are existing/added.
        for (GuestId guestId : incoming.getGuestIds()) {

            if (addedGuests.contains(guestId)) {
                // Add the guestId.
                existing.addGuestId(guestId);
                if (log.isDebugEnabled()) {
                    log.debug("New guest ID added: {}", guestId.getGuestId());
                }
                sink.queueEvent(eventFactory.guestIdCreated(guestId));
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

    /*
     * Check if this consumer is a guest, and if it appears to have migrated.
     * We only check for existing entitlements, restricted to a host that does not match
     * the guest's current host, as determined by the most recent guest ID report in the
     * db. If autobind has been disabled for the guest's owner, the host_restricted entitlements
     * from the old host are still removed, but no auto-bind occurs.
     */
    protected void checkForGuestMigration(Consumer guest) {
        if (!guest.isGuest()) {
            // This isn't a guest, skip this entire step.
            return;
        }
        else if (!guest.hasFact("virt.uuid")) {
            return;
        }

        Consumer host = consumerCurator.getHost(guest);

        // we need to create a list of entitlements to delete before actually
        // deleting, otherwise we are tampering with the loop iterator (BZ #786730)
        Set<Entitlement> deletableGuestEntitlements = new HashSet<Entitlement>();
        log.debug("Revoking {} entitlements not matching host: {}", guest, host);
        for (Entitlement entitlement : guest.getEntitlements()) {
            Pool pool = entitlement.getPool();

            // If there is no host required or the pool isn't for unmapped guests, skip it
            if (!(pool.hasAttribute(Pool.Attributes.REQUIRES_HOST) || pool.isUnmappedGuestPool() ||
                isVirtOnly(pool))) {
                continue;
            }

            if (pool.hasAttribute(Pool.Attributes.REQUIRES_HOST)) {
                String requiredHost = getRequiredHost(pool);
                if (host != null && !requiredHost.equals(host.getUuid())) {
                    log.debug("Removing entitlement {} from guest {} due to host mismatch.",
                        entitlement.getId(), guest.getUuid());
                    deletableGuestEntitlements.add(entitlement);
                }
            }
            else if (pool.isUnmappedGuestPool() && host != null) {
                log.debug("Removing unmapped guest pool from {} now that it is mapped", guest.getUuid());
                deletableGuestEntitlements.add(entitlement);
            }
        }
        // perform the entitlement revocation
        for (Entitlement entitlement : deletableGuestEntitlements) {
            poolManager.revokeEntitlement(entitlement);
        }

        if (deletableGuestEntitlements.size() > 0) {
            // auto heal guests after revocations
            boolean hasInstalledProducts = guest.getInstalledProducts() != null &&
                !guest.getInstalledProducts().isEmpty();

            if (guest.isAutoheal() && !deletableGuestEntitlements.isEmpty() && hasInstalledProducts) {
                AutobindData autobindData = AutobindData.create(guest).on(new Date());
                // Autobind could be disabled for the owner. If it is, we simply don't
                // perform the autobind for the guest.
                try {
                    List<Entitlement> ents = entitler.bindByProducts(autobindData);
                    entitler.sendEvents(ents);
                }
                catch (AutobindDisabledForOwnerException e) {
                    log.warn("Guest auto-attach skipped. {}", e.getMessage());
                }
            }
        }
    }

    private String getRequiredHost(Pool pool) {
        String value = pool.getAttributeValue(Pool.Attributes.REQUIRES_HOST);
        return value != null ? value : "";
    }

    private boolean isVirtOnly(Pool pool) {
        String value = pool.getAttributeValue(Pool.Attributes.VIRT_ONLY);
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    @ApiOperation(notes = "Removes a Consumer", value = "deleteConsumer")
    @ApiResponses({ @ApiResponse(code = 403, message = ""), @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}")
    @Transactional
    public void deleteConsumer(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid,
        @Context Principal principal) {
        log.debug("Deleting consumer_uuid {}", uuid);

        Consumer toDelete = consumerCurator.findByUuid(uuid);
        this.consumerCurator.lock(toDelete);

        try {
            this.poolManager.revokeAllEntitlements(toDelete);
        }
        catch (ForbiddenException e) {
            String msg = e.message().getDisplayMessage();
            throw new ForbiddenException(i18n.tr("Cannot unregister {0} {1} because: {2}",
                toDelete.getType().getLabel(), toDelete.getName(), msg), e);

        }
        consumerRules.onConsumerDelete(toDelete);

        Event event = eventFactory.consumerDeleted(toDelete);
        consumerCurator.delete(toDelete);
        identityCertService.deleteIdentityCert(toDelete);
        sink.queueEvent(event);
    }

    private void logShareConsumerRequestWarning(String api, Consumer consumer) {
        log.warn("skipping {} request for share consumer {} of org {} and of recipient org {}",
            api, consumer.getUuid(), consumer.getOwner().getKey(), consumer.getRecipientOwnerKey());
    }

    @ApiOperation(notes = "Retrieves a list of Entitlement Certificates for the Consumer",
        value = "getEntitlementCertificates")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("{consumer_uuid}/certificates")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Certificate> getEntitlementCertificates(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("serials") String serials) {

        log.debug("Getting client certificates for consumer: {}", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        if (consumer.isShare()) {
            logShareConsumerRequestWarning("cert fetch", consumer);
            return new ArrayList<Certificate>();
        }

        checkForGuestMigration(consumer);
        poolManager.regenerateDirtyEntitlements(consumer);

        Set<Long> serialSet = this.extractSerials(serials);

        List<Certificate> returnCerts = new LinkedList<Certificate>();
        List<EntitlementCertificate> allCerts = entCertService
            .listForConsumer(consumer);
        for (EntitlementCertificate cert : allCerts) {
            if (serialSet.isEmpty() ||
                serialSet.contains(cert.getSerial().getId())) {
                returnCerts.add(cert);
            }
        }
        // we want to insert the content access cert to this list if appropriate
        try {
            Certificate cert = contentAccessCertService.getCertificate(consumer);
            if (cert != null) {
                returnCerts.add(cert);
            }
        }
        catch (IOException ioe) {
            throw new BadRequestException(i18n.tr("Cannot retrieve content access certificate"), ioe);
        }
        catch (GeneralSecurityException gse) {
            throw new BadRequestException(i18n.tr("Cannot retrieve content access certificate"), gse);
        }
        return returnCerts;
    }

    @ApiOperation(notes = "Retrieves the body of the Content Access Certificate for the Consumer",
        value = "getContentAccessBody", response = String.class)
    @ApiResponses({ @ApiResponse(code = 404, message = ""), @ApiResponse(code = 304, message = "") })
    @GET
    @Path("{consumer_uuid}/accessible_content")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getContentAccessBody(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @HeaderParam("If-Modified-Since") @DefaultValue("Thu, 01 Jan 1970 00:00:00 GMT")
        @DateFormat({"EEE, dd MMM yyyy HH:mm:ss z"}) Date since) {

        log.debug("Getting content access certificate for consumer: {}", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.isShare()) {
            throw new BadRequestException(i18n.tr("Content access body " +
               "can not be requested for a share consumer"));
        }
        if (consumer.getOwner().getContentAccessMode() != null &&
            !consumer.getOwner().getContentAccessMode()
            .equals(ContentAccessCertServiceAdapter.ORG_ENV_ACCESS_MODE)) {
            throw new BadRequestException(i18n.tr("Content access mode does not allow this request."));
        }
        if (!contentAccessCertService.hasCertChangedSince(consumer, since)) {
            return Response.status(Response.Status.NOT_MODIFIED)
                .entity("Not modified since date supplied.").build();
        }
        ContentAccessListing result = new ContentAccessListing();

        try {
            ContentAccessCertificate cac = contentAccessCertService.getCertificate(consumer);
            if (cac == null) {
                throw new BadRequestException(i18n.tr("Cannot retrieve content access certificate"));
            }

            String cert = cac.getCert();
            String certificate = cert.substring(0, cert.indexOf("-----BEGIN ENTITLEMENT DATA-----\n"));
            String json = cert.substring(cert.indexOf("-----BEGIN ENTITLEMENT DATA-----\n"));
            List<String> pieces = new ArrayList<String>();
            pieces.add(certificate);
            pieces.add(json);
            result.setContentListing(cac.getSerial().getId(), pieces);
            result.setLastUpdate(cac.getUpdated());
        }
        catch (IOException ioe) {
            throw new BadRequestException(i18n.tr("Cannot retrieve content access certificate"), ioe);
        }
        catch (GeneralSecurityException gse) {
            throw new BadRequestException(i18n.tr("Cannot retrieve content access certificate", gse));
        }
        return Response.ok(result, MediaType.APPLICATION_JSON).build();
    }

    @ApiOperation(notes = "Retrieves a Compressed File of Entitlement Certificates",
        value = "exportCertificates")
    @ApiResponses({ @ApiResponse(code = 500, message = ""), @ApiResponse(code = 404, message = "") })
    @GET
    @Produces("application/zip")
    @Path("/{consumer_uuid}/certificates")
    public File exportCertificates(
        @Context HttpServletResponse response,
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("serials") String serials) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        if (consumer.isShare()) {
            logShareConsumerRequestWarning("cert export", consumer);
            return null;
        }

        checkForGuestMigration(consumer);
        Set<Long> serialSet = this.extractSerials(serials);
        // filtering requires a null set, so make this null if it is
        // empty
        if (serialSet.isEmpty()) {
            serialSet = null;
        }

        File archive;
        try {
            archive = manifestManager.generateEntitlementArchive(consumer, serialSet);
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

    @ApiOperation(
        notes = "Retrieves a list of Certiticate Serials Return the " +
        "client certificate metadata a for the given consumer. This is a small" +
        " subset of data clients can use to determine which certificates they" +
        " need to update/fetch.",
        value = "getEntitlementCertificateSerials")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("{consumer_uuid}/certificates/serials")
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "serials")
    public List<CertificateSerialDto> getEntitlementCertificateSerials(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        log.debug("Getting client certificate serials for consumer: {}", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        if (consumer.isShare()) {
            logShareConsumerRequestWarning("cert serial fetch", consumer);
            return new ArrayList<CertificateSerialDto>();
        }

        checkForGuestMigration(consumer);
        poolManager.regenerateDirtyEntitlements(consumer);

        List<CertificateSerialDto> allCerts = new LinkedList<CertificateSerialDto>();
        for (Long id : entCertService.listEntitlementSerialIds(consumer)) {
            allCerts.add(new CertificateSerialDto(id));
        }
        // add content access cert if needed
        try {
            ContentAccessCertificate cac = contentAccessCertService.getCertificate(consumer);
            if (cac != null) {
                allCerts.add(new CertificateSerialDto(cac.getSerial().getId()));
            }
        }
        catch (IOException ioe) {
            throw new BadRequestException(i18n.tr("Cannot retrieve content access certificate"), ioe);
        }
        catch (GeneralSecurityException gse) {
            throw new BadRequestException(i18n.tr("Cannot retrieve content access certificate", gse));
        }
        return allCerts;
    }

    private void validateBindArguments(boolean hasPoolQuantities, String poolIdString, Integer quantity,
        String[] productIds, List<String> fromPools, Date entitleDate, Consumer consumer, boolean async) {
        short parameters = 0;

        if (consumer.isShare() && StringUtils.isBlank(poolIdString) && !hasPoolQuantities) {
            throw new BadRequestException(i18n.tr("Share consumers must be bound to a specific pool"));
        }

        if (hasPoolQuantities) {
            parameters++;
        }
        if (poolIdString != null) {
            parameters++;
        }
        if (ArrayUtils.isNotEmpty(productIds) || CollectionUtils.isNotEmpty(fromPools) ||
            entitleDate != null) {
            parameters++;
        }
        if (parameters > 1) {
            throw new BadRequestException(i18n.tr("Cannot bind by multiple parameters."));
        }

        if (hasPoolQuantities) {
            if (quantity != null) {
                throw new BadRequestException(
                        i18n.tr("Cannot specify a single quantity when binding a batch of " +
                                " exact pools. Please specify a quantity for each pool"));
            }
            else if (!async) {
                throw new BadRequestException(i18n.tr("Batch bind can only be performed asynchronously"));
            }
        }

        if (poolIdString == null && quantity != null) {
            throw new BadRequestException(i18n.tr("Cannot specify a quantity when auto-binding."));
        }

    }

    @ApiOperation(notes = "If a pool ID is specified, we know we're binding to that exact pool. " +
        "Specifying an entitle date in this case makes no sense and will throw an " +
        "error. If a list of product IDs are specified, we attempt to auto-bind to" +
        " subscriptions which will provide those products. An optional date can be" +
        " specified allowing the consumer to get compliant for some date in the " +
        "future. If no date is specified we assume the current date. If neither a " +
        "pool nor an ID is specified, this is a healing request. The path is similar " +
        "to the bind by products, but in this case we use the installed products on " +
        "the consumer, and their current compliant status, to determine which product" +
        " IDs should be requested. The entitle date is used the same as with bind by " +
        "products. The response will contain a list of Entitlement objects if async is" +
        " false, or a JobDetail object if async is true.", value = "Bind Entitlements")
    @ApiResponses({ @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 403, message = "Binds Entitlements"), @ApiResponse(code = 404, message = "") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/entitlements")
    @SuppressWarnings("checkstyle:indentation")
    public Response bind(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("pool") @Verify(value = Pool.class, nullable = true,
            subResource = SubResource.ENTITLEMENTS) String poolIdString,
        @QueryParam("product") String[] productIds,
        @QueryParam("quantity") Integer quantity,
        @QueryParam("email") String email,
        @QueryParam("email_locale") String emailLocale,
        @QueryParam("async") @DefaultValue("false") boolean async,
        @QueryParam("entitle_date") String entitleDateStr,
        @QueryParam("from_pool") List<String> fromPools,
        PoolIdAndQuantity[] poolQuantities,
        @Context Principal principal) {
        boolean hasPoolQuantities = (poolQuantities != null && poolQuantities.length > 0);
        // TODO: really should do this in a before we get to this call
        // so the method takes in a real Date object and not just a String.
        Date entitleDate = ResourceDateParser.parseDateString(entitleDateStr);

        // Verify consumer exists:
        Consumer consumer = consumerCurator.verifyAndLookupConsumerWithEntitlements(consumerUuid);
        log.debug("Consumer (post verify): {}", consumer);

        // Check that only one query param was set, and some other validations
        validateBindArguments(hasPoolQuantities, poolIdString, quantity, productIds, fromPools,
            entitleDate, consumer, async);

        if (hasPoolQuantities) {
            Map<String, PoolIdAndQuantity> pqMap = new HashMap<String, PoolIdAndQuantity>();
            for (PoolIdAndQuantity poolQuantity : poolQuantities) {
                if (pqMap.containsKey(poolQuantity.getPoolId())) {
                    pqMap.get(poolQuantity.getPoolId()).addQuantity(poolQuantity.getQuantity());
                }
                else {
                    pqMap.put(poolQuantity.getPoolId(), poolQuantity);
                }
            }
            int batchBindLimit = config.getInt(ConfigProperties.BATCH_BIND_NUMBER_OF_POOLS_LIMIT);
            if (pqMap.keySet().size() > batchBindLimit) {
                throw new BadRequestException(i18n.tr(
                    "Cannot bind more than {0} pools per request, found: {1}",
                    batchBindLimit, pqMap.keySet().size()
                ));
            }

            List<Pool> pools = poolManager.secureFind(pqMap.keySet());
            if (!principal.canAccessAll(pools, SubResource.ENTITLEMENTS, Access.CREATE)) {
                throw new NotFoundException(i18n.tr("Pools with ids {0} could not be found.",
                    pqMap.keySet()));
            }
        }

        try {
            // I hate double negatives, but if they have accepted all
            // terms, we want comeToTerms to be true.
            long subTermsStart = System.currentTimeMillis();
            if (!consumer.isShare() && subAdapter.hasUnacceptedSubscriptionTerms(consumer.getOwner())) {
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
            else if (hasPoolQuantities) {
                detail = EntitlerJob.bindByPoolAndQuantities(consumer, poolQuantities);
            }
            else {
                detail = EntitleByProductsJob.bindByProducts(productIds, consumer, entitleDate, fromPools);
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
            entitlements = entitler.bindByPoolQuantity(consumer, poolIdString, quantity);
        }
        else {
            try {
                AutobindData autobindData = AutobindData.create(consumer).on(entitleDate)
                    .forProducts(productIds).withPools(fromPools);
                entitlements = entitler.bindByProducts(autobindData);
            }
            catch (AutobindDisabledForOwnerException e) {
                throw new BadRequestException(i18n.tr("Ignoring request to auto-attach. " +
                    "It is disabled for org ''{0}''.", consumer.getOwner().getKey()));
            }
        }

        // we need to supply the compliance type for the pools
        // the method in this class does not do quantity
        if (entitlements != null) {
            for (Entitlement ent : entitlements) {
                addCalculatedAttributes(ent);
            }
        }
        // Trigger events:
        entitler.sendEvents(entitlements);

        return Response.status(Response.Status.OK)
            .type(MediaType.APPLICATION_JSON).entity(entitlements).build();
    }

    @ApiOperation(notes = "Retrieves a list of Pools and quantities that would be the " +
        "result of an auto-bind. This is a dry run of an autobind. It allows the client " +
        "to see what would be the result of an autobind without executing it. It can only" +
        " do this for the prevously established list of installed products for the consumer" +
        " If a service level is included in the request, then that level will override " +
        "the one stored on the consumer. If no service level is included then the existing " +
        "one will be used. The Response has a list of PoolQuantity objects", value = "dryBind")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 403, message = ""),
        @ApiResponse(code = 404, message = "") })
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/entitlements/dry-run")
    public List<PoolQuantity> dryBind(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("service_level") String serviceLevel) {

        // Verify consumer exists:
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        if (consumer.getOwner().autobindDisabled()) {
            throw new BadRequestException(i18n.tr("Owner has autobind disabled."));
        }

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

    @ApiOperation(notes = "Retrives a list of Entitlements", value = "listEntitlements")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
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

        if (regen) {
            checkForGuestMigration(consumer);
        }

        EntitlementFilterBuilder filters = EntitlementFinderUtil.createFilter(matches, attrFilters);
        Page<List<Entitlement>> entitlementsPage = entitlementCurator.listByConsumer(consumer, productId,
            filters, pageRequest);

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, entitlementsPage);

        if (regen) {
            poolManager.regenerateDirtyEntitlements(entitlementsPage.getPageData());
        }
        else {
            log.debug("Skipping certificate regeneration.");
        }
        // we need to supply the compliance type for the pools
        // the method in this class does not do quantity
        if (entitlementsPage.getPageData() != null) {
            for (Entitlement ent : entitlementsPage.getPageData()) {
                addCalculatedAttributes(ent);
            }
        }

        return entitlementsPage.getPageData();
    }

    @ApiOperation(notes = "Retrieves the Owner associated to a Consumer", value = "getOwner")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/owner")
    public Owner getOwner(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        return consumer.getOwner();
    }

    @ApiOperation(
        notes = "Unbinds all Entitlements for a Consumer Result contains the " +
        "total number of entitlements unbound.",
        value = "unbindAll")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/entitlements")
    public DeleteResult unbindAll(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {

        // FIXME: just a stub, needs CertifcateService (and/or a
        // CertificateCurator) to lookup by serialNumber
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        int total = poolManager.revokeAllEntitlements(consumer);
        log.debug("Revoked {} entitlements from {}", total, consumerUuid);
        return new DeleteResult(total);

        // Need to parse off the value of subscriptionNumberArgs, probably
        // use comma separated see IntergerList in sparklines example in
        // jersey examples find all entitlements for this consumer and
        // subscription numbers delete all of those (and/or return them to
        // entitlement pool)
    }

    @ApiOperation(notes = "Removes an Entitlement from a Consumer By the Entitlement ID", value = "unbind")
    @ApiResponses({ @ApiResponse(code = 403, message = ""), @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.WILDCARD)
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

    @ApiOperation(notes = "Removes an Entitlement from a Consumer By the Certificate Serial",
        value = "unbindBySerial")
    @ApiResponses({ @ApiResponse(code = 403, message = ""), @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.WILDCARD)
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

    @ApiOperation(notes = "Removes all Entitlements from a Consumer. By Pool Id", value = "unbindByPool")
    @ApiResponses({ @ApiResponse(code = 403, message = ""), @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.WILDCARD)
    @Path("/{consumer_uuid}/entitlements/pool/{pool_id}")
    public void unbindByPool(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @PathParam("pool_id") String poolId) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        List<Entitlement> entitlementsToDelete = entitlementCurator
            .listByConsumerAndPoolId(consumer, poolId);
        if (!entitlementsToDelete.isEmpty()) {
            for (Entitlement toDelete: entitlementsToDelete) {
                poolManager.revokeEntitlement(toDelete);
            }
        }
        else {
            throw new NotFoundException(i18n.tr(
                "No entitlements for consumer ''{0}'' with pool id ''{1}''", consumerUuid, poolId));
        }
    }

    @ApiOperation(notes = "Retrieves a list of Consumer Events", value = "getConsumerEvents")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}/events")
    public List<Event> getConsumerEvents(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        List<Event> events = this.eventCurator.listMostRecent(FEED_LIMIT, consumer).list();
        if (events != null) {
            eventAdapter.addMessageText(events);
        }
        return events;
    }

    @ApiOperation(notes = "Retrieves and Event Atom Feed for a Consumer", value = "getConsumerAtomFeed")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces("application/atom+xml")
    @Path("/{consumer_uuid}/atom")
    public Feed getConsumerAtomFeed(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        String path = String.format("/consumers/%s/atom", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Feed feed = this.eventAdapter.toFeed(
            this.eventCurator.listMostRecent(FEED_LIMIT, consumer).list(), path);
        feed.setTitle("Event feed for consumer " + consumer.getUuid());
        return feed;
    }

    @ApiOperation(notes = "Regenerates the Entitlement Certificates for a Consumer",
        value = "regenerateEntitlementCertificates")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @PUT
    @Produces(MediaType.WILDCARD)
    @Consumes(MediaType.WILDCARD)
    @Path("/{consumer_uuid}/certificates")
    public void regenerateEntitlementCertificates(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("entitlement") String entitlementId,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        Consumer c = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (c.isShare()) {
            logShareConsumerRequestWarning("cert regen", c);
            return;
        }

        if (entitlementId != null) {
            Entitlement e = verifyAndLookupEntitlement(entitlementId);
            poolManager.regenerateCertificatesOf(e, lazyRegen);
        }
        else {
            poolManager.regenerateCertificatesOf(c, lazyRegen);
        }
    }

    /**
     * Retrieves a compressed file representation of a Consumer (manifest).
     *
     * @deprecated use GET /consumers/:consumer_uuid/export/async
     * @param response
     * @param consumerUuid
     * @param cdnLabel
     * @param webAppPrefix
     * @param apiUrl
     * @return the generated file archive.
     */
    @Deprecated
    @ApiOperation(
        notes = "Retrieves a Compressed File representation of a Consumer (manifest).",
        value = "Consumer Export (manifest)",
        response = File.class)
    @ApiResponses({ @ApiResponse(code = 403, message = ""), @ApiResponse(code = 500, message = ""),
        @ApiResponse(code = 404, message = "") })
    @Produces("application/zip")
    @GET
    @Path("{consumer_uuid}/export")
    public File exportData(
        @Context HttpServletResponse response,
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("cdn_label") String cdnLabel,
        @QueryParam("webapp_prefix") String webAppPrefix,
        @QueryParam("api_url") String apiUrl,
        @QueryParam("ext") @CandlepinParam(type = KeyValueParameter.class)
        @ApiParam(value = "Key/Value pairs to be passed to the extension adapter when generating a manifest",
        required = false, example = "ext=version:1.2.3&ext=extension_key:EXT1")
        List<KeyValueParameter> extensionArgs) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.isShare()) {
            throw new BadRequestException(i18n.tr("Can not export manifest of a share consumer"));
        }

        try {
            File archive = manifestManager.generateManifest(consumerUuid, cdnLabel, webAppPrefix, apiUrl,
                getExtensionParamMap(extensionArgs));
            response.addHeader("Content-Disposition", "attachment; filename=" + archive.getName());
            return archive;
        }
        catch (ExportCreationException e) {
            throw new IseException(i18n.tr("Unable to create export archive"), e);
        }
    }

    /**
     * Initiates an async generation of a compressed file representation of a {@link Consumer} (manifest).
     * The response will contain the id of the job from which its result data will contain the href to
     * download the generated file.
     *
     * @param response the response to send back from the server.
     * @param consumerUuid the uuid of the target consumer.
     * @param cdnLabel the CDN label to store in the meta file.
     * @param webAppPrefix the URL pointing to the manifest's originating web application.
     * @param apiUrl the API URL pointing to the manifest's originating candlepin API.
     * @return the details of the async export job that is to be started.
     */
    @ApiOperation(
        notes = "Initiates an async generation of a Compressed File representation of a Consumer " +
        "(manifest). The response will contain the id of the job from which its result data " +
        " will contain the href to download the generated file.",
        value = "Async Consumer Export (manifest)",
        response = JobDetail.class)
    @ApiResponses({ @ApiResponse(code = 403, message = ""), @ApiResponse(code = 500, message = ""),
        @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}/export/async")
    public JobDetail exportDataAsync(
        @Context HttpServletResponse response,
        @PathParam("consumer_uuid") @Verify(Consumer.class)
        @ApiParam(value = "The UUID of the target consumer", required = true) String consumerUuid,
        @QueryParam("cdn_label")
        @ApiParam(value = "The lable of the target CDN", required = false)
        String cdnLabel,
        @QueryParam("webapp_prefix")
        @ApiParam(value = "the URL pointing to the manifest's originating web application", required = false)
        String webAppPrefix,
        @QueryParam("api_url")
        @ApiParam(value = "the URL pointing to the manifest's originating candlepin API", required = false)
        String apiUrl,
        @QueryParam("ext") @CandlepinParam(type = KeyValueParameter.class)
        @ApiParam(value = "Key/Value pairs to be passed to the extension adapter when generating a manifest",
        required = false, example = "ext=version:1.2.3&ext=extension_key:EXT1")
        List<KeyValueParameter> extensionArgs) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.isShare()) {
            throw new BadRequestException(i18n.tr("Can not export manifest of a share consumer"));
        }
        return manifestManager.generateManifestAsync(consumerUuid, cdnLabel, webAppPrefix, apiUrl,
            getExtensionParamMap(extensionArgs));
    }

    /**
     * Builds a map of String -> String from a list of {@link KeyValueParameter} query parameters
     * where param.key is the map key and param.value is the map value.
     *
     * @param params the query parameters to build the map from.
     * @return a Map<String, String> of the key/value pairs in the specified parameters.
     */
    private Map<String, String> getExtensionParamMap(List<KeyValueParameter> params) {
        Map<String, String> paramMap = new HashMap<String, String>();
        for (KeyValueParameter param : params) {
            paramMap.put(param.key(), param.value());
        }
        return paramMap;
    }

    /**
     * Downloads an asynchronously generated consumer export file (manifest). If the file
     * was successfully downloaded, it will be deleted.
     *
     * @param response
     * @param consumerUuid the UUID of the target consumer.
     * @param exportId the id of the stored export.
     */
    @ApiOperation(
        notes = "Downloads an asynchronously generated consumer export file (manifest).",
        value = "Async Consumer Export (manifest) Download",
        response = File.class)
    @ApiResponses({ @ApiResponse(code = 403, message = ""), @ApiResponse(code = 500, message = ""),
        @ApiResponse(code = 404, message = "") })
    @GET
    @Produces("application/zip")
    @Path("{consumer_uuid}/export/{export_id}")
    public void downloadExistingExport(
        @Context HttpServletResponse response,
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @PathParam("export_id") String exportId) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.isShare()) {
            throw new BadRequestException(i18n.tr("Can not export manifest of a share consumer"));
        }
        // *******************************************************************************
        // NOTE: If changing the path or parameters of this end point, be sure to update
        // the HREF generation in ConsumerResource.buildAsyncDownloadManifestHref.
        // *******************************************************************************

        // The response for this request is formulated a little different for this
        // file download. In some cases, such as for a hibernate DB file service, we must
        // stream the results from the DB to the client by directly writing to the
        // response output stream.
        //
        // NOTE: Passing the database input stream to the response builder seems
        //       like it would be a correct approach here, but large object streaming
        //       can only be done inside a single transaction, so we have to stream it
        //       manually.
        // TODO See if there is a way to get RestEasy to do this so we don't have to.
        manifestManager.writeStoredExportToResponse(exportId, consumerUuid, response);

        // On successful manifest read, delete the record. The manifest can only be
        // downloaded once and must then be regenerated.
        manifestManager.deleteStoredManifest(exportId);
    }

    /**
     * Builds an HREF to a stored manifest file.
     *
     * @param consumerUuid the target consumer UUID.
     * @param manifestId the target manifest ID.
     * @return the HREF string for the specified manifest
     */
    public static String buildAsyncDownloadManifestHref(String consumerUuid, String manifestId) {
        return String.format("/consumers/%s/export/%s", consumerUuid, manifestId);
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
    @ApiOperation(notes = "Retrieves a single Consumer", value = "regenerateIdentityCertificates")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("{consumer_uuid}")
    public Consumer regenerateIdentityCertificates(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid) {
        Consumer c = consumerCurator.verifyAndLookupConsumer(uuid);
        if (c.isShare()) {
            logShareConsumerRequestWarning("regenerate identity certificate", c);
            return c;
        }
        return regenerateIdentityCertificate(c);
    }

    /**
     * Identity Certificate regeneration for a given consumer.
     * Includes persistence of the certificate and consumer
     *
     * @param consumer consumer that will get new identity cert
     * @return a Consumer object
     */
    private Consumer regenerateIdentityCertificate(Consumer consumer) {
        EventBuilder eventBuilder = eventFactory
            .getEventBuilder(Target.CONSUMER, Type.MODIFIED)
            .setOldEntity(consumer);

        IdentityCertificate ic = generateIdCert(consumer, true);
        consumer.setIdCert(ic);
        consumerCurator.update(consumer);
        sink.queueEvent(eventBuilder.setNewEntity(consumer).buildEvent());
        return consumer;
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

    @ApiOperation(notes = "Retrieves a list of Guest Consumers of a Consumer", value = "getGuests")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/guests")
    public List<Consumer> getGuests(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        return consumerCurator.getGuests(consumer);
    }

    @ApiOperation(notes = "Retrieves a Host Consumer of a Consumer", value = "getHost")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{consumer_uuid}/host")
    public Consumer getHost(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @Context Principal principal) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.getFact("virt.uuid") == null ||
            consumer.getFact("virt.uuid").trim().equals("")) {
            throw new BadRequestException(i18n.tr("The system with UUID {0} is not a virtual guest.",
                consumer.getUuid()));
        }

        Consumer host = consumerCurator.getHost(consumer);

        // The host would be in a different organization if a host-restricted pool has been shared into the
        // current organization.
        if (host == null || principal.canAccess(host.getOwner(), SubResource.CONSUMERS, Access.READ_ONLY)) {
            return host;
        }

        throw new ForbiddenException(
            i18n.tr("This host is under a different organization that you do not have access to")
        );
    }

    @ApiOperation(notes = "Retrieves the Release of a Consumer", value = "getRelease")
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


    @ApiOperation(notes = "Retireves the Compliance Status of a Consumer.", value = "getComplianceStatus")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{consumer_uuid}/compliance")
    @Transactional
    public ComplianceStatus getComplianceStatus(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String uuid,
        @ApiParam("Date to get compliance information for, default is now.")
        @QueryParam("on_date") String onDate) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);
        Date date = ResourceDateParser.parseDateString(onDate);
        if (consumer.isShare()) {
            logShareConsumerRequestWarning("fetch compliance", consumer);
            return new ComplianceStatus(date);
        }
        return this.complianceRules.getStatus(consumer, date);
    }

    @ApiOperation(notes = "Retrieves a Compliance Status list for a list of Consumers",
        value = "getComplianceStatusList")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/compliance")
    @Transactional
    public Map<String, ComplianceStatus> getComplianceStatusList(
        @QueryParam("uuid") @Verify(value = Consumer.class, nullable = true) List<String> uuids) {

        Map<String, ComplianceStatus> results = new HashMap<String, ComplianceStatus>();

        if (uuids != null && !uuids.isEmpty()) {
            for (Consumer consumer : consumerCurator.findByUuids(uuids)) {
                ComplianceStatus status;
                if (consumer.isShare()) {
                    logShareConsumerRequestWarning("fetch compliance", consumer);
                    status = new ComplianceStatus(null);
                }
                else {
                    status = complianceRules.getStatus(consumer, null);
                }
                results.put(consumer.getUuid(), status);
            }
        }

        return results;
    }

    @ApiOperation(
        notes = "Removes the Deletion Record for a Consumer Allowed for a superadmin." +
        " The main use case for this would be if a user accidently deleted a " +
        "non-RHEL hypervisor, causing it to no longer be auto-detected via virt-who.",
        value = "removeDeletionRecord")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
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
