/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.EntitleByProductsJob;
import org.candlepin.async.tasks.EntitlerJob;
import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SecurityHole;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.UpdateConsumerCheckIn;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.Verify;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.AutobindDisabledForOwnerException;
import org.candlepin.controller.AutobindHypervisorDisabledException;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.controller.EntitlementCertificateGenerator;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.CertificateDTO;
import org.candlepin.dto.api.server.v1.CertificateSerialDTO;
import org.candlepin.dto.api.server.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.server.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.server.v1.ContentAccessDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.dto.api.server.v1.DeleteResult;
import org.candlepin.dto.api.server.v1.EntitlementDTO;
import org.candlepin.dto.api.server.v1.EnvironmentDTO;
import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.dto.api.server.v1.HypervisorIdDTO;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.PoolQuantityDTO;
import org.candlepin.dto.api.server.v1.ReleaseVerDTO;
import org.candlepin.dto.api.server.v1.SystemPurposeComplianceStatusDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.GoneException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Certificate;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerActivationKey;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerCurator.ConsumerQueryArguments;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.InvalidOrderKeyException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.compliance.hash.ComplianceFacts;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.dto.ContentAccessListing;
import org.candlepin.resource.server.v1.ConsumerApi;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.EntitlementEnvironmentFilter;
import org.candlepin.resource.util.EnvironmentUpdates;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.util.KeyValueStringParser;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.UserInfo;
import org.candlepin.sync.ExportCreationException;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.FactValidator;
import org.candlepin.util.PropertyValidationException;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.OptimisticLockException;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


/**
 * API Gateway for Consumers
 */
public class ConsumerResource implements ConsumerApi {
    private static final Logger log = LoggerFactory.getLogger(ConsumerResource.class);

    /** The maximum number of consumers to return per list or find request */
    private static final int MAX_CONSUMERS_PER_REQUEST = 1000;

    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final SubscriptionServiceAdapter subAdapter;
    private final ProductServiceAdapter prodAdapter;
    private final EntitlementCurator entitlementCurator;
    private final IdentityCertServiceAdapter identityCertService;
    private final EntitlementCertServiceAdapter entCertService;
    private final ContentAccessManager contentAccessManager;
    private final UserServiceAdapter userService;
    private final I18n i18n;
    private final EventSink sink;
    private final EventFactory eventFactory;
    private final PoolManager poolManager;
    private final RefresherFactory refresherFactory;
    private final ConsumerRules consumerRules;
    private final OwnerCurator ownerCurator;
    private final ActivationKeyCurator activationKeyCurator;
    private final Entitler entitler;
    private final ComplianceRules complianceRules;
    private final SystemPurposeComplianceRules systemPurposeComplianceRules;
    private final DeletedConsumerCurator deletedConsumerCurator;
    private final EnvironmentCurator environmentCurator;
    private final DistributorVersionCurator distributorVersionCurator;
    private final Configuration config;
    private final CalculatedAttributesUtil calculatedAttributesUtil;
    private final ConsumerBindUtil consumerBindUtil;
    private final ManifestManager manifestManager;
    private final FactValidator factValidator;
    private final ConsumerTypeValidator consumerTypeValidator;
    private final ConsumerEnricher consumerEnricher;
    private final Provider<GuestMigration> migrationProvider;
    private final ModelTranslator translator;
    private final JobManager jobManager;
    private final DTOValidator validator;
    private final PrincipalProvider principalProvider;
    private final ContentOverrideValidator coValidator;
    private final ConsumerContentOverrideCurator ccoCurator;
    private final EntitlementCertificateGenerator entCertGenerator;

    private final EntitlementEnvironmentFilter entitlementEnvironmentFilter;
    private final Pattern consumerSystemNamePattern;
    private final Pattern consumerPersonNamePattern;

    @Inject
    @SuppressWarnings({"checkstyle:parameternumber"})
    public ConsumerResource(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        SubscriptionServiceAdapter subAdapter,
        ProductServiceAdapter prodAdapter,
        EntitlementCurator entitlementCurator,
        IdentityCertServiceAdapter identityCertService,
        EntitlementCertServiceAdapter entCertServiceAdapter,
        I18n i18n,
        EventSink sink,
        EventFactory eventFactory,
        UserServiceAdapter userService,
        PoolManager poolManager,
        RefresherFactory refresherFactory,
        ConsumerRules consumerRules,
        OwnerCurator ownerCurator,
        ActivationKeyCurator activationKeyCurator,
        Entitler entitler,
        ComplianceRules complianceRules,
        SystemPurposeComplianceRules systemPurposeComplianceRules,
        DeletedConsumerCurator deletedConsumerCurator,
        EnvironmentCurator environmentCurator,
        DistributorVersionCurator distributorVersionCurator,
        Configuration config,
        CalculatedAttributesUtil calculatedAttributesUtil,
        ConsumerBindUtil consumerBindUtil,
        ManifestManager manifestManager,
        ContentAccessManager contentAccessManager,
        FactValidator factValidator,
        ConsumerTypeValidator consumerTypeValidator,
        ConsumerEnricher consumerEnricher,
        Provider<GuestMigration> migrationProvider,
        ModelTranslator translator,
        JobManager jobManager,
        DTOValidator validator,
        PrincipalProvider principalProvider,
        ContentOverrideValidator coValidator,
        ConsumerContentOverrideCurator ccoCurator,
        EntitlementCertificateGenerator entCertGenerator,
        EnvironmentContentCurator environmentContentCurator) {

        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.subAdapter = Objects.requireNonNull(subAdapter);
        this.prodAdapter = Objects.requireNonNull(prodAdapter);
        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.identityCertService = Objects.requireNonNull(identityCertService);
        this.entCertService = Objects.requireNonNull(entCertServiceAdapter);
        this.i18n = Objects.requireNonNull(i18n);
        this.sink = Objects.requireNonNull(sink);
        this.eventFactory = Objects.requireNonNull(eventFactory);
        this.userService = Objects.requireNonNull(userService);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.refresherFactory = Objects.requireNonNull(refresherFactory);
        this.consumerRules = Objects.requireNonNull(consumerRules);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
        this.entitler = Objects.requireNonNull(entitler);
        this.complianceRules = Objects.requireNonNull(complianceRules);
        this.systemPurposeComplianceRules = Objects.requireNonNull(systemPurposeComplianceRules);
        this.deletedConsumerCurator = Objects.requireNonNull(deletedConsumerCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
        this.distributorVersionCurator = Objects.requireNonNull(distributorVersionCurator);
        this.config = Objects.requireNonNull(config);
        this.calculatedAttributesUtil = Objects.requireNonNull(calculatedAttributesUtil);
        this.consumerBindUtil = Objects.requireNonNull(consumerBindUtil);
        this.manifestManager = Objects.requireNonNull(manifestManager);
        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
        this.factValidator = Objects.requireNonNull(factValidator);
        this.consumerTypeValidator = Objects.requireNonNull(consumerTypeValidator);
        this.consumerEnricher = Objects.requireNonNull(consumerEnricher);
        this.migrationProvider = Objects.requireNonNull(migrationProvider);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.validator = Objects.requireNonNull(validator);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.coValidator = Objects.requireNonNull(coValidator);
        this.ccoCurator = Objects.requireNonNull(ccoCurator);
        this.entCertGenerator = Objects.requireNonNull(entCertGenerator);

        this.entitlementEnvironmentFilter = new EntitlementEnvironmentFilter(
            entitlementCurator, environmentContentCurator);
        this.consumerPersonNamePattern = Pattern.compile(config.getString(
            ConfigProperties.CONSUMER_PERSON_NAME_PATTERN));
        this.consumerSystemNamePattern = Pattern.compile(config.getString(
            ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN));
    }

    /**
     * Validates the specified owner key and resolves it to an Owner object. If the key is not valid
     * or cannot be resolved to an organization, this method throws a NotFoundException
     *
     * @param ownerKey
     *  the key of the owner to validate and resolve
     *
     * @throws NotFoundException
     *  if the provided key cannot be resolved to an Owner
     *
     * @return
     *  the resolved owner object
     */
    private Owner validateOwnerKey(String ownerKey) {
        Owner owner = this.ownerCurator.getByKey(ownerKey);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Organization {0} does not exist", ownerKey));
        }

        return owner;
    }

    @Override
    @SecurityHole
    @RootResource.LinkedResource
    public Iterable<ContentOverrideDTO> listConsumerContentOverrides(String consumerUuid) {
        Principal principal = ResteasyContext.getContextData(Principal.class);
        Consumer parent = this.verifyAndGetParent(consumerUuid, principal, Access.READ_ONLY);

        CandlepinQuery<ConsumerContentOverride> query = this.ccoCurator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
    }

    @Override
    @Transactional
    @SecurityHole
    public Iterable<ContentOverrideDTO> addConsumerContentOverrides(
        String consumerUuid, List<ContentOverrideDTO> entries) {

        // Validate our input
        this.coValidator.validate(entries);
        Principal principal = ResteasyContext.getContextData(Principal.class);
        // Fetch the "parent" content override object...
        Consumer parent = this.verifyAndGetParent(consumerUuid, principal, Access.ALL);

        try {
            for (ContentOverrideDTO dto : entries) {
                ConsumerContentOverride override = this.ccoCurator
                    .retrieve(parent, dto.getContentLabel(), dto.getName());

                // We're counting on Hibernate to do our batching for us here...
                if (override != null) {
                    override.setValue(dto.getValue());
                    this.ccoCurator.merge(override);
                }
                else {
                    override = new ConsumerContentOverride();

                    override.setParent(parent);
                    override.setContentLabel(dto.getContentLabel());
                    override.setName(dto.getName());
                    override.setValue(dto.getValue());

                    this.ccoCurator.create(override);
                }
            }
        }
        catch (RuntimeException e) {
            // Make sure we clear all pending changes, since we don't want to risk storing only a
            // portion of the changes.
            this.ccoCurator.clear();

            // Re-throw the exception
            throw e;
        }

        // Hibernate typically persists automatically before executing a query against a table with
        // pending changes, but if it doesn't, we can add a flush here to make sure this outputs the
        // correct values
        CandlepinQuery<ConsumerContentOverride> query = this.ccoCurator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
    }

    @Override
    @Transactional
    @SecurityHole
    public Iterable<ContentOverrideDTO> deleteConsumerContentOverrides(
        String consumerUuid, List<ContentOverrideDTO> entries) {

        Principal principal = ResteasyContext.getContextData(Principal.class);
        Consumer parent = this.verifyAndGetParent(consumerUuid, principal, Access.ALL);

        if (entries.size() == 0) {
            this.ccoCurator.removeByParent(parent);
        }
        else {
            for (ContentOverrideDTO dto : entries) {
                String label = dto.getContentLabel();
                if (StringUtils.isBlank(label)) {
                    this.ccoCurator.removeByParent(parent);
                }
                else {
                    String name = dto.getName();
                    if (StringUtils.isBlank(name)) {
                        this.ccoCurator.removeByContentLabel(parent, dto.getContentLabel());
                    }
                    else {
                        this.ccoCurator.removeByName(parent, dto.getContentLabel(), name);
                    }
                }
            }
        }

        CandlepinQuery<ConsumerContentOverride> query = this.ccoCurator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
    }

    private Consumer verifyAndGetParent(String parentId, Principal principal, Access access) {
        // Throws exception if criteria block the id
        Consumer result = this.consumerCurator.verifyAndLookupConsumer(parentId);

        // Now that we know it exists, verify access level
        if (!principal.canAccess(result, SubResource.NONE, access)) {
            throw new ForbiddenException(i18n.tr("Insufficient permissions"));
        }

        return result;
    }

    /**
     * Sanitizes inbound consumer facts, truncating long facts and dropping invalid or untracked
     * facts. The consumer DTO will be updated in-place.
     *
     * @param consumerDTO
     *  The consumer DTO to populate with sanitized facts
     */
    public void sanitizeConsumerFacts(ConsumerDTO consumerDTO) {
        if (consumerDTO != null) {
            Map<String, String> facts = consumerDTO.getFacts();

            if (facts != null && facts.size() > 0) {
                log.debug("Sanitizing facts for consumer {}", consumerDTO.getName());
                Map<String, String> sanitized = sanitizeConsumerFacts(facts);
                consumerDTO.setFacts(sanitized);
            }
        }
    }

    /**
     * Sanitizes inbound consumer facts, truncating long facts and dropping invalid or untracked
     * facts. The consumer will be updated in-place.
     *
     * @param consumer
     *  The entity to populate with sanitized facts
     */
    public void sanitizeConsumerFacts(Consumer consumer) {
        if (consumer != null) {
            Map<String, String> facts = consumer.getFacts();

            if (facts != null && facts.size() > 0) {
                log.debug("Sanitizing facts for consumer {}", consumer.getName());
                Map<String, String> sanitized = sanitizeConsumerFacts(facts);
                consumer.setFacts(sanitized);
            }
        }
    }

    /**
     * Sanitizes inbound consumer facts, truncating long facts and dropping invalid or untracked facts.
     *
     * @param facts the facts that are to be sanitized
     *
     * @return the sanitized facts
     */
    private Map<String, String> sanitizeConsumerFacts(Map<String, String> facts) {
        Map<String, String> sanitized = new HashMap<>();
        Set<String> lowerCaseKeys = new HashSet<>();

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
                log.debug("  Consumer fact \"{}\" does not match pattern \"{}\"", key, factPattern);
                log.debug("  Discarding fact \"{}\"...", key);
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
                log.warn("  {}", e.getMessage(), e);
                log.warn("  Discarding fact \"{}\"...", key);
                continue;
            }

            sanitized.put(key, value);
            lowerCaseKeys.add(lowerCaseKey);
        }

        return sanitized;
    }

    @Override
    @Wrapped(element = "consumers")
    @SuppressWarnings("checkstyle:indentation")
    public Stream<ConsumerDTOArrayElement> searchConsumers(
        String username,
        Set<String> typeLabels,
        String ownerKey,
        List<String> uuids,
        List<String> hypervisorIds,
        List<String> facts,
        Integer page, Integer perPage, String order, String sortBy) {

        if ((username == null || username.isEmpty()) &&
            (typeLabels == null || typeLabels.isEmpty()) &&
            (ownerKey == null || ownerKey.isEmpty()) &&
            (uuids == null || uuids.isEmpty()) &&
            (hypervisorIds == null || hypervisorIds.isEmpty()) &&
            (facts == null || facts.isEmpty())) {

            throw new BadRequestException(i18n.tr("Must specify at least one search criteria."));
        }

        Owner owner = ownerKey != null ? this.validateOwnerKey(ownerKey) : null;
        List<ConsumerType> types = this.consumerTypeValidator.findAndValidateTypeLabels(typeLabels);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setOwner(owner)
            .setUsername(username)
            .setUuids(uuids)
            .setTypes(types)
            .setHypervisorIds(hypervisorIds);

        new KeyValueStringParser(this.i18n).parseKeyValuePairs(facts)
            .forEach(kvpair -> queryArgs.addFact(kvpair.getKey(), kvpair.getValue()));

        long count = this.consumerCurator.getConsumerCount(queryArgs);
        log.debug("Consumer query will fetch {} consumers", count);

        // Do paging bits, if necessary
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        if (pageRequest != null) {
            Page<Stream<ConsumerDTOArrayElement>> pageResponse = new Page<>();
            pageResponse.setPageRequest(pageRequest);

            if (pageRequest.isPaging()) {
                queryArgs.setOffset((pageRequest.getPage() - 1) * pageRequest.getPerPage())
                    .setLimit(pageRequest.getPerPage());
            }

            if (pageRequest.getSortBy() != null) {
                boolean reverse = pageRequest.getOrder() == PageRequest.Order.DESCENDING;
                queryArgs.addOrder(pageRequest.getSortBy(), reverse);
            }

            pageResponse.setMaxRecords((int) count);

            // Store the page for the LinkHeaderResponseFilter
            ResteasyContext.pushContext(Page.class, pageResponse);
        }
        // If no paging was specified, force a limit on amount of results
        else {
            if (count > MAX_CONSUMERS_PER_REQUEST) {
                String errmsg = this.i18n.tr("This endpoint does not support returning more than {0} " +
                    "results at a time, please use paging.", MAX_CONSUMERS_PER_REQUEST);
                throw new BadRequestException(errmsg);
            }
        }

        try {
            return this.consumerCurator.findConsumers(queryArgs).stream()
                .map(this.translator.getStreamMapper(Consumer.class, ConsumerDTOArrayElement.class));
        }
        catch (InvalidOrderKeyException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @Override
    public void consumerExists(@Verify(Consumer.class) String uuid) {
        if (!consumerCurator.doesConsumerExist(uuid)) {
            throw new NotFoundException(i18n.tr("Consumer with id {0} could not be found.", uuid));
        }
    }

    @Override
    public Response consumerExistsBulk(Set<String> consumerUuids) {
        if (consumerUuids != null && !consumerUuids.isEmpty()) {
            Set<String> existingUuids = consumerCurator.getExistingConsumerUuids(consumerUuids);
            consumerUuids.removeAll(existingUuids);

            if (consumerUuids.isEmpty()) {
                return Response.status(Response.Status.NO_CONTENT).build();
            }
            else {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(consumerUuids).build();
            }
        }
        else {
            throw new BadRequestException(i18n.tr("No UUIDs provided."));
        }
    }

    @Override
    public ConsumerDTO getConsumer(@Verify(Consumer.class) String uuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);

        if (consumer != null) {
            IdentityCertificate idcert = consumer.getIdCert();

            if (idcert != null) {
                Date expire = idcert.getSerial().getExpiration();
                int days = config.getInt(ConfigProperties.IDENTITY_CERT_EXPIRY_THRESHOLD);
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

        return this.translator.translate(consumer, ConsumerDTO.class);
    }

    @Override
    public ContentAccessDTO getContentAccessForConsumer(@Verify(Consumer.class) String uuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);

        Predicate<String> predicate = (str) -> str != null && !str.isEmpty();

        String caMode = Util.firstOf(predicate,
            consumer.getContentAccessMode(),
            consumer.getOwner().getContentAccessMode(),
            ContentAccessManager.ContentAccessMode.getDefault().toDatabaseValue()
        );

        String caList = Util.firstOf(predicate,
            consumer.getOwner().getContentAccessModeList(),
            ContentAccessManager.getListDefaultDatabaseValue()
        );

        return new ContentAccessDTO()
            .contentAccessMode(caMode)
            .contentAccessModeList(Util.toList(caList));
    }

    /**
     * Populates the specified entity with data from the provided DTO, during consumer creation (not update).
     * This method will not set the ID, entitlementStatus, complianceStatusHash, idCert, entitlements,
     * keyPair and canActivate, because clients are not allowed to create or update those properties.
     *
     * while autoheal is populated, it is overridden in create method.
     *
     * owner is not populated because create populates it differently.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void populateEntity(Consumer entity, ConsumerDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the consumer model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the consumer dto is null");
        }

        if (dto.getCreated() != null) {
            entity.setCreated(Util.toDate(dto.getCreated()));
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getUuid() != null) {
            entity.setUuid(dto.getUuid());
        }

        if (dto.getFacts() != null) {
            entity.setFacts(dto.getFacts());
        }

        if (dto.getUsername() != null) {
            entity.setUsername(dto.getUsername());
        }

        if (dto.getServiceLevel() != null) {
            entity.setServiceLevel(dto.getServiceLevel());
        }

        if (dto.getRole() != null) {
            entity.setRole(dto.getRole());
        }

        if (dto.getUsage() != null) {
            entity.setUsage(dto.getUsage());
        }

        if (dto.getServiceType() != null) {
            entity.setServiceType(dto.getServiceType());
        }

        if (dto.getAddOns() != null) {
            entity.setAddOns(dto.getAddOns());
        }

        if (dto.getReleaseVer() != null) {
            entity.setReleaseVer(new Release(dto.getReleaseVer().getReleaseVer()));
        }

        if (dto.getLastCheckin() != null) {
            entity.setLastCheckin(Util.toDate(dto.getLastCheckin()));
        }

        if (dto.getCapabilities() != null) {
            Set<ConsumerCapability> capabilities = populateCapabilities(entity, dto);
            entity.setCapabilities(capabilities);
        }

        if (dto.getGuestIds() != null) {
            List<GuestId> guestIds = new ArrayList<>();
            for (GuestIdDTO guestIdDTO : dto.getGuestIds()) {
                if (guestIdDTO != null) {
                    guestIds.add(new GuestId(guestIdDTO.getGuestId(),
                        entity,
                        guestIdDTO.getAttributes()));
                }
            }
            entity.setGuestIds(guestIds);
        }

        if (dto.getHypervisorId() != null && entity.getOwnerId() != null) {
            HypervisorId hid = new HypervisorId()
                .setOwner(this.ownerCurator.findOwnerById(entity.getOwnerId()))
                .setConsumer(entity)
                .setHypervisorId(dto.getHypervisorId().getHypervisorId())
                .setReporterId(dto.getHypervisorId().getReporterId());

            entity.setHypervisorId(hid);
        }

        if (dto.getHypervisorId() == null &&
            getFactValue(dto.getFacts(), Consumer.Facts.DMI_SYSTEM_UUID) != null &&
            !"true".equals(getFactValue(dto.getFacts(), Consumer.Facts.VIRT_IS_GUEST)) &&
            entity.getOwnerId() != null) {

            HypervisorId hid = new HypervisorId()
                .setOwner(this.ownerCurator.findOwnerById(entity.getOwnerId()))
                .setConsumer(entity)
                .setHypervisorId(this.getFactValue(dto.getFacts(), Consumer.Facts.DMI_SYSTEM_UUID));

            entity.setHypervisorId(hid);
        }

        if (dto.getContentTags() != null) {
            entity.setContentTags(dto.getContentTags());
        }

        if (dto.getAutoheal() != null) {
            entity.setAutoheal(dto.getAutoheal());
        }

        if (dto.getAnnotations() != null) {
            entity.setAnnotations(dto.getAnnotations());
        }

        if (dto.getInstalledProducts() != null) {
            Set<ConsumerInstalledProduct> installedProducts = populateInstalledProducts(entity, dto);
            entity.setInstalledProducts(installedProducts);
        }
    }

    /**
     * Utility method that translates a ConsumerDTO's capabilities to Consumer model entity capabilities.
     *
     * @param entity the Consumer model entity which the capabilities are to reference
     *
     * @param dto the ConsumerDTO whose capabilities we want to translate
     *
     * @return the model entity ConsumerCapabilities set that was created
     */
    private Set<ConsumerCapability> populateCapabilities(Consumer entity, ConsumerDTO dto) {
        return dto.getCapabilities().stream()
            .filter(Objects::nonNull)
            .map(cdto -> new ConsumerCapability(cdto.getName()))
            .collect(Collectors.toSet());
    }

    /**
     * Utility method that translates a ConsumerDTO's installed products to
     * Consumer model entity installed products.
     *
     * @param entity the Consumer model entity which the installed products are to reference
     *
     * @param dto the ConsumerDTO whose installed products we want to translate
     *
     * @return the model entity ConsumerInstalledProduct set that was created
     */
    private Set<ConsumerInstalledProduct> populateInstalledProducts(Consumer entity, ConsumerDTO dto) {
        Set<ConsumerInstalledProduct> installedProducts = new HashSet<>();

        for (ConsumerInstalledProductDTO installedProductDTO : dto.getInstalledProducts()) {
            if (installedProductDTO != null) {
                ConsumerInstalledProduct installedProduct = new ConsumerInstalledProduct()
                    .setProductId(installedProductDTO.getProductId())
                    .setProductName(installedProductDTO.getProductName())
                    .setVersion(installedProductDTO.getVersion())
                    .setArch(installedProductDTO.getArch())
                    .setStatus(installedProductDTO.getStatus())
                    .setStartDate(Util.toDate(installedProductDTO.getStartDate()))
                    .setEndDate(Util.toDate(installedProductDTO.getEndDate()));

                installedProducts.add(installedProduct);
            }
        }

        return installedProducts;
    }

    @Override
    @Transactional
    @SecurityHole(activationKey = true)
    public ConsumerDTO createConsumer(ConsumerDTO dto, String userName, String ownerKey,
        String activationKeys, Boolean identityCertCreation) {

        this.validator.validateCollectionElementsNotNull(dto::getInstalledProducts,
            dto::getGuestIds, dto::getCapabilities);
        Principal principal = this.principalProvider.get();

        // Resolve or create owner if needed
        Owner owner = setupOwner(principal, ownerKey);

        // fix for duplicate hypervisor/consumer problem
        Consumer consumer = null;
        if (config.getBoolean(ConfigProperties.USE_SYSTEM_UUID_FOR_MATCHING) &&
            getFactValue(dto.getFacts(), Consumer.Facts.DMI_SYSTEM_UUID) != null &&
            !"true".equalsIgnoreCase(getFactValue(dto.getFacts(), Consumer.Facts.VIRT_IS_GUEST))) {

            consumer = consumerCurator.getHypervisor(
                getFactValue(dto.getFacts(), Consumer.Facts.DMI_SYSTEM_UUID), owner);
            if (consumer != null) {
                consumer.setIdCert(generateIdCert(consumer, false));
                this.updateConsumer(consumer.getUuid(), dto);
                return translator.translate(consumer, ConsumerDTO.class);
            }
        }

        if (dto.getType() == null) {
            throw new BadRequestException(i18n.tr("Unit type must be specified."));
        }

        ConsumerType ctype = this.consumerTypeCurator.getByLabel(dto.getType().getLabel());
        if (ctype == null) {
            throw new BadRequestException(i18n.tr("Invalid unit type: {0}", dto.getType().getLabel()));
        }

        Consumer created = createConsumerFromDTO(dto, ctype, principal, userName, owner, activationKeys,
            identityCertCreation);

        return this.translator.translate(created, ConsumerDTO.class);
    }

    public Consumer createConsumerFromDTO(ConsumerDTO consumer, ConsumerType type, Principal principal,
        String userName, Owner owner, String activationKeys, boolean identityCertCreation)
        throws BadRequestException {

        validateOnKeyStrings(activationKeys, owner.getKey(), userName);
        List<ActivationKey> keys = findActivationKeysInOrder(owner, activationKeys);

        userName = setUserName(consumer, principal, userName);
        checkConsumerName(consumer);

        validateViaConsumerType(consumer, type, keys, owner, userName, principal);

        if (consumer.getAutoheal() != null) {
            consumer.setAutoheal(consumer.getAutoheal());
        }
        else {
            consumer.setAutoheal(true); // this is the default
        }

        // Sanitize the inbound facts
        this.sanitizeConsumerFacts(consumer);

        Consumer consumerToCreate = new Consumer();

        consumerToCreate.setOwner(owner);

        populateEntity(consumerToCreate, consumer);
        consumerToCreate.setType(type);

        consumerToCreate.setCanActivate(subAdapter.canActivateSubscription(consumerToCreate));

        HypervisorId hvsrId = consumerToCreate.getHypervisorId();
        if (hvsrId != null && hvsrId.getHypervisorId() != null && !hvsrId.getHypervisorId().isEmpty()) {
            // If a hypervisorId is supplied, make sure the consumer and owner are correct
            hvsrId.setConsumer(consumerToCreate);
            hvsrId.setOwner(owner);
        }

        updateCapabilities(consumerToCreate, null);
        logNewConsumerDebugInfo(consumerToCreate, keys, type);

        this.setContentAccessMode(consumer, type, owner, consumerToCreate);

        Set<ConsumerActivationKey> consumerAks = keys.stream()
            .map(key -> new ConsumerActivationKey(key.getId(), key.getName()))
            .collect(Collectors.toSet());

        consumerToCreate.setActivationKeys(consumerAks);

        try {
            Date createdDate = consumerToCreate.getCreated();
            Date lastCheckIn = consumerToCreate.getLastCheckin();

            this.updateEnvironments(consumer, consumerToCreate, false);

            // create sets created to current time.
            consumerToCreate = consumerCurator.create(consumerToCreate);

            //  If we sent in a created date, we want it persisted at the update below
            if (createdDate != null) {
                consumerToCreate.setCreated(createdDate);
            }

            if (lastCheckIn != null) {
                log.info("Creating with specific last check-in time: {}", lastCheckIn);
                consumerToCreate.setLastCheckin(lastCheckIn);
            }

            if (identityCertCreation) {
                IdentityCertificate idCert = generateIdCert(consumerToCreate, false);
                consumerToCreate.setIdCert(idCert);
            }

            sink.emitConsumerCreated(consumerToCreate);

            // Update syspurpose data
            // Note: this must come before activation key handling, as syspurpose details on the
            // activation keys have higher priority than those on consumer
            this.updateSystemPurposeData(consumer, consumerToCreate);

            // If no service level was specified, and the owner has a default set, use it:
            String csl = consumerToCreate.getServiceLevel();
            if ((csl == null || csl.isEmpty())) {
                consumerToCreate.setServiceLevel(owner.getDefaultServiceLevel() != null ?
                    owner.getDefaultServiceLevel() :
                    "");
            }

            if (keys.size() > 0) {
                consumerBindUtil.handleActivationKeys(consumerToCreate, keys, owner.isAutobindDisabled());
            }

            // This should update compliance on consumerToCreate, but not call the curator
            complianceRules.getStatus(consumerToCreate, null, false, false);
            systemPurposeComplianceRules.getStatus(consumerToCreate, consumerToCreate.getEntitlements(),
                null, false);
            consumerToCreate = consumerCurator.update(consumerToCreate);

            log.info("Consumer {} created in org {}",
                consumerToCreate.getUuid(), consumerToCreate.getOwnerId());

            return consumerToCreate;
        }
        catch (CandlepinException ce) {
            // If it is one of ours, rethrow it.
            throw ce;
        }
        catch (Exception e) {
            log.error("Problem creating unit:", e);
            throw new BadRequestException(i18n.tr("Problem creating unit {0}", consumer), e);
        }
    }

    /**
     * We need to make sure the requested keys are available. In addition, the order in
     *  which they are specified in the call should be maintained for their later application.
     *
     * @param owner Owner to filter on
     * @param keyStrings Comma-delimited list of activation key names
     * @return List of found activation keys in the same order as the keystring
     */
    private List<ActivationKey> findActivationKeysInOrder(Owner owner, String keyStrings) {
        if (keyStrings == null || keyStrings.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, ActivationKey> keys = new LinkedHashMap<>();
        for (String keyname : keyStrings.split("\\s*,\\s*")) {
            keys.put(keyname, null);
        }

        this.activationKeyCurator.findByKeyNames(owner.getKey(), keys.keySet())
            .forEach(key -> keys.put(key.getName(), key));

        Predicate<Map.Entry<String, ActivationKey>> keyFilter = entry -> {
            if (entry.getValue() == null) {
                log.warn("Activation key \"{}\" not found for organization \"{}\".",
                    entry.getKey(), owner.getKey());
                return false;
            }
            return true;
        };

        return keys.entrySet().stream()
            .filter(keyFilter)
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    /**
     * Validates the incoming content access mode value and sets it on the destination consumer if
     * necessary.
     *
     * @param srcConsumer
     *  the consumer DTO containing the requested content access mode
     *
     * @param ctype
     *  the consumer's type
     *
     * @param owner
     *  the owner of the consumer being updated
     *
     * @param dstConsumer
     *  the destination consumer entity to update
     *
     * @throws BadRequestException
     *  if the requested content access mode is invalid or otherwise cannot be set on the consumer
     */
    private void setContentAccessMode(ConsumerDTO srcConsumer, ConsumerType ctype, Owner owner,
        Consumer dstConsumer) throws BadRequestException {

        String caMode = srcConsumer.getContentAccessMode();

        if (ctype.isManifest()) {
            if (caMode == null) {
                // ENT-3755: Use the "best available" if nothing was provided
                ContentAccessMode best = owner.isAllowedContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT) ?
                    ContentAccessMode.ORG_ENVIRONMENT :
                    ContentAccessMode.ENTITLEMENT;

                caMode = best.toDatabaseValue();
            }
            else if (caMode.isEmpty()) {
                // Clear consumer's content access mode and defer to the org's instead
                caMode = null;
            }
            else {
                // Validate user's choice and use it if possible
                if (!owner.isAllowedContentAccessMode(caMode)) {
                    throw new BadRequestException(
                        i18n.tr("The consumer cannot use the supplied content access mode."));
                }
            }

            dstConsumer.setContentAccessMode(caMode);
        }
        else {
            if (caMode != null && !caMode.isEmpty()) {
                throw new BadRequestException(
                    i18n.tr("The consumer cannot be assigned a content access mode."));
            }
        }
    }

    private void validateOnKeyStrings(String keyString, String ownerKey, String userName) {
        if (keyString != null && !keyString.isEmpty()) {
            if (ownerKey == null) {
                throw new BadRequestException(i18n.tr("Org required to register with activation keys."));
            }

            if (userName != null) {
                throw new BadRequestException(i18n.tr("Cannot specify username with activation keys."));
            }
        }
    }

    private void validateViaConsumerType(ConsumerDTO consumer, ConsumerType type, List<ActivationKey> keys,
        Owner owner, String userName, Principal principal) {
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            if (keys.size() > 0) {
                throw new BadRequestException(
                    i18n.tr("A unit type of \"person\" cannot be used with activation keys"));
            }

            if (!isConsumerPersonNameValid(consumer.getName())) {
                throw new BadRequestException(i18n.tr("System name cannot contain most special characters."));
            }

            verifyPersonConsumer(consumer, type, owner, userName, principal);
        }

        if (type.isType(ConsumerTypeEnum.SYSTEM) && !isConsumerSystemNameValid(consumer.getName())) {
            throw new BadRequestException(i18n.tr("System name cannot contain most special characters."));
        }

        HttpRequest httpRequest = ResteasyContext.getContextData(HttpRequest.class);
        if (httpRequest != null) {
            List<String> userAgent = httpRequest.getHttpHeaders().getRequestHeader("user-agent");
            if (type.isManifest() && userAgent != null &&
                userAgent.size() > 0 && userAgent.get(0).startsWith("RHSM")) {
                throw new BadRequestException(
                    i18n.tr("You may not create a manifest consumer via Subscription Manager."));
            }
        }
    }

    private String setUserName(ConsumerDTO consumer, Principal principal, String userName) {
        if (userName == null) {
            userName = principal.getUsername();
        }

        if (userName != null) {
            consumer.setUsername(userName);
        }

        return userName;
    }

    private void populateCapabilities(Consumer consumer, Collection<DistributorVersionCapability> dvcs) {
        Stream<DistributorVersionCapability> cstream = dvcs != null ? dvcs.stream() : Stream.of();
        Set<ConsumerCapability> ccaps = cstream.filter(Objects::nonNull)
            .map(dvc -> new ConsumerCapability(dvc.getName()))
            .collect(Collectors.toSet());

        consumer.setCapabilities(ccaps);
    }

    private boolean updateCapabilities(Consumer existing, ConsumerDTO update) {
        boolean change = false;
        if (update == null) {
            // create
            if ((existing.getCapabilities() == null || existing.getCapabilities().isEmpty()) &&
                existing.getFact(Consumer.Facts.DISTRIBUTOR_VERSION) != null) {

                Set<DistributorVersionCapability> capabilities = distributorVersionCurator
                    .findCapabilitiesByDistVersion(existing.getFact(Consumer.Facts.DISTRIBUTOR_VERSION));

                this.populateCapabilities(existing, capabilities);
                change = true;
            }
        }
        else {
            // update

            // TODO: FIXME:
            // This update process seems weird, and potentially broken. We only populate distributor-
            // version-based capabilities when the capabilities are null (i.e. not sent up in the
            // update) AND the update includes facts AND those facts include the distributor version
            // -- even in cases where the DV fact is not net-new (i.e. adding, removing, or changing
            // a different fact). Additionally, we don't just populate DV facts in this case, we
            // entirely overwrite any existing capabilities that may have been set in the past.
            //
            // It seems like a better option here would be to always check the DV fact, and ensure
            // the DV-based capabilities are present in the capabilities list, adding them if they
            // don't exist, but not removing any existing capabilities.
            // This does get a bit fuzzy in cases where the DV capabilities define capabilities that
            // might be explicitly set already, but in those cases, the existing one should probably
            // win.

            if (update.getCapabilities() != null) {
                Set<ConsumerCapability> entityCapabilities = populateCapabilities(existing, update);

                if (!entityCapabilities.equals(existing.getCapabilities())) {
                    existing.setCapabilities(entityCapabilities);
                    change = true;
                }
            }
            else if (getFactValue(update.getFacts(), Consumer.Facts.DISTRIBUTOR_VERSION) != null) {
                DistributorVersion dv = distributorVersionCurator.findByName(
                    getFactValue(update.getFacts(), Consumer.Facts.DISTRIBUTOR_VERSION));

                if (dv != null) {
                    this.populateCapabilities(existing, dv.getCapabilities());
                }

                // TODO: FIXME: Shouldn't the change flag only be set if we actually change the consumer?
                // Probably move this up to the populate capabilities call above, or make the above call
                // clear capabilities when the distributor version is null.
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

    private void checkConsumerName(ConsumerDTO consumer) {
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

    private void verifyPersonConsumer(ConsumerDTO consumer, ConsumerType type, Owner owner, String username,
        Principal principal) {

        UserInfo user = null;
        try {
            user = userService.findByLogin(username);
        }
        catch (UnsupportedOperationException e) {
            log.warn("User service does not allow user lookups, cannot verify person consumer.", e);
        }

        if (user == null) {
            throw new NotFoundException(this.i18n.tr("User not found: {0}", username));
        }

        // When registering person consumers we need to be sure the username
        // has some association with the owner the consumer is destined for:
        if (!principal.canAccess(owner, SubResource.NONE, Access.ALL) && !principal.hasFullAccess()) {
            throw new ForbiddenException(i18n.tr("User \"{0}\" has no roles for organization \"{1}\"",
                user.getUsername(), owner.getKey()));
        }

        // TODO: Refactor out type specific checks?
        if (type.isType(ConsumerTypeEnum.PERSON)) {
            Consumer existing = consumerCurator.findByUsername(user.getUsername());

            if (existing != null &&
                this.consumerTypeCurator.getConsumerType(existing).isType(ConsumerTypeEnum.PERSON)) {

                // TODO: This is not the correct error code for this situation!
                throw new BadRequestException(
                    i18n.tr("User \"{0}\" has already registered a personal consumer", user.getUsername()));
            }

            consumer.setName(user.getUsername());
        }
    }

    /*
     * Resolves the owner based on the key, creating one if needed.
     * If no owner key was specified, try to resolve the owner based on the user/principal.
     *
     * Throws exception if the owner does not exist, or the user has more or less than 1 owner, or
     * if the user does not have permission to register on this owner.
     */
    protected Owner setupOwner(Principal principal, String ownerKey) {
        // If no owner was specified, try to assume based on which owners the principal has admin rights for.
        // If more than one, we have to error out.

        if (ownerKey == null && !(principal instanceof UserPrincipal)) {
            // There's no resolution we can perform here.
            log.warn("Cannot determine organization with which to register client: {}", principal);
            String errmsg = i18n.tr("Client is not authorized to register with any organization");

            throw new BadRequestException(errmsg);
        }

        if (ownerKey == null && (principal instanceof UserPrincipal)) {
            // check for this cast?
            List<String> ownerKeys = ((UserPrincipal) principal).getOwnerKeys();

            if (ownerKeys.size() != 1) {
                OwnerInfo primary = ((UserPrincipal) principal).getPrimaryOwner();
                if (primary == null) {
                    throw new BadRequestException(i18n.tr("You must specify an organization for new units."));
                }
                ownerKey = primary.getKey();
            }
            else {
                ownerKey = ownerKeys.get(0);
            }
        }

        createOwnerIfNeeded(principal);

        Owner owner = ownerCurator.getByKey(ownerKey);
        if (owner == null) {
            throw new BadRequestException(i18n.tr("Organization {0} does not exist.", ownerKey));
        }

        // Check permissions for current principal on the owner:
        if ((principal instanceof UserPrincipal) &&
            !principal.canAccess(owner, SubResource.CONSUMERS, Access.CREATE)) {

            log.warn("User {} does not have access to create consumers in org {}",
                principal.getPrincipalName(), owner.getKey());

            List<Owner> owners = ((UserPrincipal) principal).getOwners();
            boolean isOwnerContained = owners != null && owners.stream()
                .anyMatch(t -> t != null && t.getOwnerId().equals(owner.getOwnerId()));
            if (isOwnerContained) {
                throw new ForbiddenException(i18n.tr("{0} is not authorized to register with " +
                    "organization {1}", principal.getName(), owner.getKey()));
            }

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
            Owner existingOwner = ownerCurator.getByKey(owner.getKey());

            if (existingOwner == null) {
                log.info("Principal carries permission for owner that does not exist.");
                log.info("Creating new owner: {}", owner.getKey());

                existingOwner = ownerCurator.create(owner);

                this.refresherFactory.getRefresher(this.subAdapter, this.prodAdapter)
                    .add(existingOwner)
                    .run();
            }
        }
    }

    // While this is a PUT, we are treating it as a PATCH until this operation
    // becomes more prevalent. We only update the portions of the consumer that appear
    // to be set.
    @Override
    @Transactional
    @UpdateConsumerCheckIn
    public void updateConsumer(@Verify(Consumer.class) String uuid, ConsumerDTO dto) {
        this.validator.validateCollectionElementsNotNull(dto::getInstalledProducts,
            dto::getGuestIds, dto::getCapabilities);

        Principal principal = this.principalProvider.get();
        Consumer toUpdate = consumerCurator.verifyAndLookupConsumer(uuid);
        dto.setUuid(uuid);

        // Sanitize the inbound facts before applying the update
        this.sanitizeConsumerFacts(dto);

        ConsumerType toUpdateType = this.consumerTypeCurator.getConsumerType(toUpdate);

        GuestMigration guestMigration = migrationProvider.get();
        guestMigration.buildMigrationManifest(dto, toUpdate);

        if (performConsumerUpdates(dto, toUpdate, guestMigration)) {
            try {
                if (guestMigration.isMigrationPending()) {
                    guestMigration.migrate();
                }
                else {
                    consumerCurator.update(toUpdate);
                }
            }
            catch (CandlepinException ce) {
                // If it is one of ours, rethrow it.
                throw ce;
            }
            catch (Exception e) {
                log.error("Problem updating unit:", e);
                throw new BadRequestException(i18n.tr("Problem updating unit {0}", dto), e);
            }
        }
    }

    public boolean performConsumerUpdates(ConsumerDTO updated, Consumer toUpdate,
        GuestMigration guestMigration) {

        return performConsumerUpdates(updated, toUpdate, guestMigration, true);
    }

    @Transactional
    public boolean performConsumerUpdates(ConsumerDTO updated, Consumer toUpdate,
        GuestMigration guestMigration, boolean isIdCert) {

        log.debug("Updating consumer: {}", toUpdate.getUuid());

        // We need a representation of the consumer before making any modifications.
        // If nothing changes we won't send.  The new entity needs to be correct though,
        // so we should get a Jsonstring now, and finish it off if we're going to send
        EventBuilder eventBuilder = eventFactory.getEventBuilder(Target.CONSUMER, Type.MODIFIED)
            .setEventData(toUpdate);

        // version changed on non-checked in consumer, or list of capabilities
        // changed on checked in consumer
        boolean changesMade = updateCapabilities(toUpdate, updated);

        boolean complianceFactsChanged = complianceFactsChanged(updated, toUpdate);
        boolean installedProductsChanged = checkForInstalledProductsUpdate(toUpdate, updated);
        boolean guestMigrationChanged = guestMigration.isMigrationPending();
        boolean complianceChangesMade = complianceFactsChanged ||
            installedProductsChanged || guestMigrationChanged;
        changesMade = checkForFactsUpdate(toUpdate, updated) || changesMade;
        changesMade = checkForHypervisorIdUpdate(toUpdate, updated) || changesMade;

        if (updated.getContentTags() != null && !updated.getContentTags().equals(toUpdate.getContentTags())) {
            log.info("   Updating content tags.");
            toUpdate.setContentTags(updated.getContentTags());
            changesMade = true;
        }

        // Allow optional setting of the autoheal attribute:
        if (updated.getAutoheal() != null && !updated.getAutoheal().equals(toUpdate.isAutoheal())) {
            log.info("   Updating consumer autoheal setting.");
            toUpdate.setAutoheal(updated.getAutoheal());
            changesMade = true;
        }

        if (updated.getReleaseVer() != null && updated.getReleaseVer().getReleaseVer() != null) {
            String releaseVer = toUpdate.getReleaseVer() == null ?
                null : toUpdate.getReleaseVer().getReleaseVer();
            if (!updated.getReleaseVer().getReleaseVer().equals(releaseVer)) {
                log.info("   Updating consumer releaseVer setting.");
                toUpdate.setReleaseVer(new Release(updated.getReleaseVer().getReleaseVer()));
                changesMade = true;
            }
        }

        boolean sysPurposeChangesMade = updateSystemPurposeData(updated, toUpdate);

        changesMade = updateEnvironments(updated, toUpdate, true) || changesMade;

        // like the other values in an update, if consumer name is null, act as if
        // it should remain the same
        if (updated.getName() != null && !toUpdate.getName().equals(updated.getName())) {
            checkConsumerName(updated);

            log.info("Updating consumer name: {} -> {}", toUpdate.getName(), updated.getName());
            toUpdate.setName(updated.getName());
            changesMade = true;
            // get the new name into the id cert if we are using the cert
            if (isIdCert) {
                IdentityCertificate ic = generateIdCert(toUpdate, true);
                toUpdate.setIdCert(ic);
            }
        }

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(toUpdate);

        // Apply consumer-level content access changes
        String updatedContentAccessMode = updated.getContentAccessMode();
        if (updatedContentAccessMode != null) {
            if (!updatedContentAccessMode.isEmpty()) {
                if (!ctype.isManifest()) {
                    throw new BadRequestException(
                        i18n.tr("This consumer cannot be assigned a content access mode"));
                }

                Owner toUpdateOwner = toUpdate.getOwner();
                if (!toUpdateOwner.isAllowedContentAccessMode(updatedContentAccessMode)) {
                    throw new BadRequestException(i18n.tr(
                        "This consumer cannot use the supplied content access mode: {0}",
                        updatedContentAccessMode));
                }

                toUpdate.setContentAccessMode(updatedContentAccessMode);
                changesMade = true;
            }
            else if (toUpdate.getContentAccessMode() != null) {
                // Allow falling back to "inherit" mode
                toUpdate.setContentAccessMode(null);
                changesMade = true;
            }
        }

        if (updated.getLastCheckin() != null) {
            log.info("Updating to specific last checkin time: {}", updated.getLastCheckin());
            toUpdate.setLastCheckin(Util.toDate(updated.getLastCheckin()));
            changesMade = true;
        }

        changesMade = changesMade || complianceChangesMade || sysPurposeChangesMade;

        if (changesMade) {
            log.debug("Consumer {} updated.", toUpdate.getUuid());

            // Set the updated date here b/c @PreUpdate will not get fired
            // since only the facts table will receive the update.
            toUpdate.setUpdated(new Date());

            Event event = eventBuilder.setEventData(toUpdate).buildEvent();
            sink.queueEvent(event);
        }

        if (sysPurposeChangesMade) {
            // this should update compliance on toUpdate, but not call the curator
            systemPurposeComplianceRules.getStatus(toUpdate, toUpdate.getEntitlements(), null, false);
        }

        if (complianceChangesMade) {
            // this should update compliance on toUpdate, but not call the curator
            complianceRules.getStatus(toUpdate, null, false, false);
        }

        return changesMade;
    }

    private boolean complianceFactsChanged(ConsumerDTO updated, Consumer toUpdate) {
        if (updated == null || updated.getFacts() == null) {
            return false;
        }

        return !Objects.equals(ComplianceFacts.of(updated), ComplianceFacts.of(toUpdate));
    }

    /**
     * Validates and updates consumer's multiple environments.
     *
     * @param updated
     *  The incoming consumer DTO for which to fetch multiple environments object
     * @param toUpdate
     *  The consumer instance to set the validated environments
     * @param existing
     *  This identifies whether the consumer is new or existing
     *  if existing=true, it will regenerate the certificates
     *
     * @throws NotFoundException
     *  if environment is not found by provided id or name
     *
     * @throws ConflictException
     *  if environment is specified more than once
     *
     * @return boolean status whether any updates are made or not.
     */
    private boolean updateEnvironments(ConsumerDTO updated, Consumer toUpdate, boolean existing) {
        List<EnvironmentDTO> environments = updated.getEnvironments();

        if (environments == null) {
            return false;
        }

        boolean changesMade = false;
        List<String> validatedEnvIds = new ArrayList<>();

        for (EnvironmentDTO environment : environments) {
            String environmentId = environment == null ? null : environment.getId();
            String environmentName = environment == null ? null : environment.getName();

            if (environmentId != null) {
                if (!environmentCurator.exists(environmentId)) {
                    throw new NotFoundException(i18n.tr(
                        "Environment with ID \"{0}\" could not be found.", environmentId));
                }
            }
            else if (environmentName != null) {
                environmentId = this.environmentCurator
                    .getEnvironmentIdByName(toUpdate.getOwnerId(), environmentName);

                if (environmentId == null) {
                    throw new NotFoundException(i18n.tr(
                        "Environment with name \"{0}\" could not be found.", environmentName));
                }
            }
            else {
                throw new NotFoundException(i18n.tr("Environment could not be" +
                    " found using provided details."));
            }

            if (validatedEnvIds.contains(environmentId)) {
                throw new ConflictException(i18n.tr("Environment \"{0}\"" +
                    " specified more than once." + environmentId));
            }

            validatedEnvIds.add(environmentId);
        }

        if (validatedEnvIds.size() > 0) {
            List<String> preExistingEnvIds = null;

            if (!toUpdate.getEnvironmentIds().isEmpty()) {
                preExistingEnvIds = toUpdate.getEnvironmentIds();
            }

            toUpdate.setEnvironmentIds(validatedEnvIds);

            if (existing) {
                // reset content access cert
                Owner owner = ownerCurator.findOwnerById(toUpdate.getOwnerId());

                if (owner.isUsingSimpleContentAccess()) {
                    toUpdate.setContentAccessCert(null);
                    this.contentAccessManager.removeContentAccessCert(toUpdate);
                }

                // lazily regenerate certs, so the client can still work
                if (preExistingEnvIds != null) {
                    EnvironmentUpdates environmentUpdates = new EnvironmentUpdates();
                    environmentUpdates.put(toUpdate.getId(), preExistingEnvIds, validatedEnvIds);
                    Set<String> entitlementsToBeRegenerated = entitlementEnvironmentFilter
                        .filterEntitlements(environmentUpdates);

                    this.entCertGenerator
                        .regenerateCertificatesByEntitlementIds(entitlementsToBeRegenerated, true);
                }
                else {
                    this.entCertGenerator.regenerateCertificatesOf(toUpdate, true);
                }
            }

            changesMade = true;
        }

        return changesMade;
    }

    private boolean updateSystemPurposeData(ConsumerDTO updated, Consumer toUpdate) {
        boolean changesMade = false;

        // Allow optional setting of the service level attribute:
        String level = updated.getServiceLevel();
        if (level != null && !level.equals(toUpdate.getServiceLevel())) {
            log.info("   Updating consumer service level setting: \"{}\" => \"{}\"",
                toUpdate.getServiceLevel(), level);

            // BZ 1618398 Remove validation check on consumer service level
            // consumerBindUtil.validateServiceLevel(toUpdate.getOwnerId(), level);
            toUpdate.setServiceLevel(level);
            changesMade = true;
        }

        String role = updated.getRole();
        if (role != null && !role.equals(toUpdate.getRole())) {
            log.info("   Updating consumer role setting: \"{}\" => \"{}\"", toUpdate.getRole(), role);
            toUpdate.setRole(role);
            changesMade = true;
        }

        String usage = updated.getUsage();
        if (usage != null && !usage.equals(toUpdate.getUsage())) {
            log.info("   Updating consumer usage setting: \"{}\" => \"{}\"", toUpdate.getUsage(), usage);
            toUpdate.setUsage(usage);
            changesMade = true;
        }

        if (updated.getAddOns() != null && !updated.getAddOns().equals(toUpdate.getAddOns())) {
            log.info("   Updating consumer system purpose add ons: {} => {}",
                toUpdate.getAddOns(), updated.getAddOns());

            toUpdate.setAddOns(updated.getAddOns());
            changesMade = true;
        }

        String serviceType = updated.getServiceType();
        if (serviceType != null && !serviceType.equals(toUpdate.getServiceType())) {
            log.info("   Updating consumer service type setting: \"{}\" => \"{}\"",
                toUpdate.getServiceType(), serviceType);
            toUpdate.setServiceType(serviceType);
            changesMade = true;
        }

        return changesMade;
    }

    private boolean checkForHypervisorIdUpdate(Consumer existing, ConsumerDTO incoming) {
        HypervisorIdDTO incomingId = incoming.getHypervisorId();
        if (incomingId != null) {
            HypervisorId existingId = existing.getHypervisorId();
            if (incomingId.getHypervisorId() == null || incomingId.getHypervisorId().isEmpty()) {
                // Allow hypervisorId to be removed
                existing.setHypervisorId(null);
            }
            else {
                Owner owner = ownerCurator.findOwnerById(existing.getOwnerId());
                if ((existingId == null ||
                    !incomingId.getHypervisorId().equals(existingId.getHypervisorId())) &&
                    isUsedByOwner(incomingId, owner)) {

                    throw new BadRequestException(i18n.tr(
                        "Problem updating unit {0}. Hypervisor id: {1} is already used.",
                        incoming, incomingId.getHypervisorId()));
                }

                if (existingId != null) {
                    if (existingId.getHypervisorId() != null &&
                        !existingId.getHypervisorId().equals(incomingId.getHypervisorId())) {

                        existingId.setHypervisorId(incomingId.getHypervisorId());
                        existingId.setOwner(owner);
                    }
                    else {
                        return false;
                    }
                }
                else {
                    // Safer to build a new clean HypervisorId object
                    HypervisorId hid = new HypervisorId()
                        .setOwner(owner)
                        .setHypervisorId(incomingId.getHypervisorId());

                    existing.setHypervisorId(hid);
                }
            }

            return true;
        }
        return false;
    }

    private boolean isUsedByOwner(HypervisorIdDTO hypervisor, Owner owner) {
        if (hypervisor == null) {
            return false;
        }
        return consumerCurator.getHypervisor(hypervisor.getHypervisorId(), owner) != null;
    }

    /**
     * Check if the consumers facts have changed. If they do not appear to have been
     * specified in this PUT, skip updating facts entirely. It returns true if facts
     * were included in request and have changed
     *
     * @param existing existing consumer
     * @param incomingFacts incoming facts
     * @return a boolean
     */
    private boolean checkForFactsUpdate(Consumer existing, Map<String, String> incomingFacts) {
        if (incomingFacts == null) {
            log.debug("Facts not included in this consumer update, skipping update.");
            return false;
        }
        else if (!existing.factsAreEqual(incomingFacts)) {
            log.info("Updating facts.");
            existing.setFacts(incomingFacts);
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
    public boolean checkForFactsUpdate(Consumer existing, Consumer incoming) {
        return this.checkForFactsUpdate(existing, incoming.getFacts());
    }

    /**
     * Check if the consumers facts have changed. If they do not appear to have been
     * specified in this PUT, skip updating facts entirely. It returns true if facts
     * were included in request and have changed
     *
     * @param existing existing consumer
     * @param incomingDTO incoming consumer DTO
     * @return a boolean
     */
    public boolean checkForFactsUpdate(Consumer existing, ConsumerDTO incomingDTO) {
        return this.checkForFactsUpdate(existing, incomingDTO.getFacts());
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
    private boolean checkForInstalledProductsUpdate(Consumer existing, ConsumerDTO incoming) {
        if (incoming.getInstalledProducts() == null) {
            log.debug("Installed packages not included in this consumer update, skipping update.");
            return false;
        }

        Set<ConsumerInstalledProduct> incomingInstalled = populateInstalledProducts(existing, incoming);
        Set<ConsumerInstalledProduct> existingInstalled = existing.getInstalledProducts();

        if (existingInstalled == null || !existingInstalled.equals(incomingInstalled)) {
            log.info("Updating installed products...");
            existing.setInstalledProducts(incomingInstalled);

            return true;
        }

        log.debug("No change to installed products.");
        return false;
    }

    /*
     * Check if this consumer is a guest, and if it appears to have migrated.
     * We only check for existing entitlements, restricted to a host that does not match
     * the guest's current host, as determined by the most recent guest ID report in the
     * db. If autobind has been disabled for the guest's owner, the host_restricted entitlements
     * from the old host are still removed, but no auto-bind occurs.
     */
    protected void revokeOnGuestMigration(Consumer guest) {
        if (guest == null || !guest.isGuest() || !guest.hasFact(Consumer.Facts.VIRT_UUID)) {
            // No consumer provided, it's not a guest or it doesn't have a virt UUID
            return;
        }

        String guestVirtUuid = guest.getFact(Consumer.Facts.VIRT_UUID);

        Consumer host = consumerCurator.getHost(guestVirtUuid, guest.getOwnerId());

        // we need to create a list of entitlements to delete before actually
        // deleting, otherwise we are tampering with the loop iterator (BZ #786730)
        Set<Entitlement> deletableGuestEntitlements = new HashSet<>();
        log.debug("Revoking {} entitlements not matching host: {}", guest, host);

        for (Entitlement entitlement : guest.getEntitlements()) {
            Pool pool = entitlement.getPool();

            // If there is no host required, the pool isn't for unmapped guests, or the pool is not
            // virt-only then the host-guest dynamic doesn't apply, so skip it.
            if (!pool.hasAttribute(Pool.Attributes.REQUIRES_HOST) && !pool.isUnmappedGuestPool() &&
                !isVirtOnly(pool)) {
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
                Owner owner = this.ownerCurator.findOwnerById(guest.getOwnerId());
                AutobindData autobindData = new AutobindData(guest, owner)
                    .on(new Date());

                // Autobind could be disabled for the owner. If it is, we simply don't
                // perform the autobind for the guest.
                try {
                    List<Entitlement> ents = entitler.bindByProducts(autobindData);
                    entitler.sendEvents(ents);
                }
                catch (AutobindDisabledForOwnerException | AutobindHypervisorDisabledException e) {
                    log.warn("Guest auto-attach skipped. {}", e.getMessage(), e);
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

    @Override
    @Transactional
    public void deleteConsumer(@Verify(Consumer.class) String uuid) {
        log.debug("Deleting consumer_uuid {}", uuid);
        Principal principal = this.principalProvider.get();
        Consumer toDelete = consumerCurator.findByUuid(uuid);
        // The consumer may have already been deleted if multiple requests come in at the same time.
        // NOTE: The Verify on the Consumer class should handle cases where a 404 should be thrown
        //       for a consumer that has never existed.
        if (toDelete == null) {
            throw new GoneException(i18n.tr("Consumer with UUID {0} was already deleted.", uuid));
        }

        try {
            this.consumerCurator.lock(toDelete);
        }
        catch (OptimisticLockException e) {
            DeletedConsumer deleted = deletedConsumerCurator.findByConsumerUuid(uuid);
            if (deleted != null) {
                log.debug("The consumer with UUID {} was deleted while waiting for lock.", uuid);
                throw new GoneException(
                    i18n.tr("Consumer with UUID {0} was already deleted.", uuid));
            }
            // Could have just been an update that caused the exception. In that case,
            // just rethrow the exception.
            throw e;
        }

        try {
            // We're about to delete this consumer; no need to regen/dirty its dependent
            // entitlements or recalculate status.
            this.poolManager.revokeAllEntitlements(toDelete, false);
        }
        catch (ForbiddenException e) {
            ConsumerType ctype = this.consumerTypeCurator.get(toDelete.getTypeId());

            String msg = e.message().getDisplayMessage();
            throw new ForbiddenException(i18n.tr("Cannot unregister {0} {1} because: {2}",
                ctype != null ? ctype.getLabel() : "unknown type", toDelete.getName(), msg), e);
        }

        consumerRules.onConsumerDelete(toDelete);
        contentAccessManager.removeContentAccessCert(toDelete);
        Event event = eventFactory.consumerDeleted(toDelete);
        consumerCurator.delete(toDelete);
        identityCertService.deleteIdentityCert(toDelete);
        sink.queueEvent(event);
    }

    /**
     * Method to get entitlement certificates.
     * NOTE: Here we explicitly update consumer Check-In.
     *
     * @param consumerUuid
     *  Consumer UUID
     *
     * @param serials
     *  Certificate serial
     *
     * @return
     *  List of DTOs representing certificates
     */
    public List<CertificateDTO> getEntitlementCertificates(@Verify(Consumer.class) String consumerUuid,
        String serials) {
        log.debug("Getting client certificates for consumer: {}", consumerUuid);

        // UpdateConsumerCheckIn
        // Explicitly updating consumer check-in,
        // as we merged getEntitlementCertificates & exportCertificates methods due to OpenAPI
        // constraint which doesn't allow more than one HTTP method key under same URL pattern.

        Principal principal = ResteasyContext.getContextData(Principal.class);
        if (principal instanceof ConsumerPrincipal) {
            ConsumerPrincipal p = (ConsumerPrincipal) principal;
            consumerCurator.updateLastCheckin(p.getConsumer());
        }

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        revokeOnGuestMigration(consumer);
        poolManager.regenerateDirtyEntitlements(consumer);

        Set<Long> serialSet = this.extractSerials(serials);
        List<? extends Certificate> entitlementCerts = this.entCertService.listForConsumer(consumer);
        Certificate caCert = this.contentAccessManager.getCertificate(consumer);
        Stream<? extends Certificate> certStream = this.buildCertificateStream(entitlementCerts, caCert);

        // Check if we should filter certs by the cert serial
        if (serialSet != null && !serialSet.isEmpty()) {
            certStream = certStream
                .filter(cert -> cert.getSerial() != null && serialSet.contains(cert.getSerial().getId()));
        }

        return certStream.map(this.translator.getStreamMapper(Certificate.class, CertificateDTO.class))
            .collect(Collectors.toList());
    }

    /**
     * Builds a stream containing all of the certificates in the provided collection, and any
     * additional certificates that might not be included in the collection (such as the content
     * access cert). Null elements will be filtered from the resulting stream.
     *
     * @param certificates
     *  a collection of certificates to include in the certificate stream
     *
     * @param extraCerts
     *  any additional certificates to include in the certificate stream
     *
     * @return
     *  a stream containing a combination of all the certificates
     */
    private Stream<? extends Certificate> buildCertificateStream(
        Collection<? extends Certificate> certificates, Certificate... extraCerts) {

        Stream<? extends Certificate> baseCertStream = certificates != null ?
            certificates.stream() :
            Stream.empty();

        Stream<? extends Certificate> extraCertStream = extraCerts != null ?
            Stream.of(extraCerts) :
            Stream.empty();

        return Stream.concat(baseCertStream, extraCertStream)
            .filter(Objects::nonNull);
    }

    @Override
    public Response getContentAccessBody(@Verify(Consumer.class) String consumerUuid, String sinceDate) {
        log.debug("Getting content access certificate for consumer: {}", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        OffsetDateTime since = null;

        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());
        if (!owner.isUsingSimpleContentAccess()) {
            throw new BadRequestException(i18n.tr("Content access mode does not allow this request."));
        }

        if (sinceDate != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(sinceDate, formatter);
            since = zonedDateTime.toOffsetDateTime();
        }


        if (!this.contentAccessManager.hasCertChangedSince(consumer, since != null ?
            Util.toDate(since) : new Date(0))) {

            return Response.status(Response.Status.NOT_MODIFIED)
                .entity("Not modified since date supplied.")
                .build();
        }

        ContentAccessCertificate cac = this.contentAccessManager.getCertificate(consumer);
        if (cac == null) {
            throw new BadRequestException(i18n.tr("Cannot retrieve content access certificate"));
        }

        String cert = cac.getCert();
        String certificate = cert.substring(0, cert.indexOf("-----BEGIN ENTITLEMENT DATA-----\n"));
        String json = cert.substring(cert.indexOf("-----BEGIN ENTITLEMENT DATA-----\n"));
        List<String> pieces = new ArrayList<>();
        pieces.add(certificate);
        pieces.add(json);

        ContentAccessListing result = new ContentAccessListing()
            .setContentListing(cac.getSerial().getId(), pieces)
            .setLastUpdate(cac.getUpdated());

        return Response.ok(result, MediaType.APPLICATION_JSON)
            .build();
    }

    @Override
    public Object exportCertificates(@Verify(Consumer.class) String consumerUuid, String serials) {
        HttpRequest httpRequest = ResteasyContext.getContextData(HttpRequest.class);

        if (httpRequest.getHttpHeaders().getRequestHeader("accept").contains("application/json")) {
            return getEntitlementCertificates(consumerUuid, serials);
        }

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        HttpServletResponse response = ResteasyContext.getContextData(HttpServletResponse.class);
        revokeOnGuestMigration(consumer);

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
        Set<Long> serialSet = new HashSet<>();
        if (serials != null && !serials.isEmpty()) {
            log.debug("Requested serials: {}", serials);
            for (String s : serials.split(",")) {
                log.debug("   {}", s);
                serialSet.add(Long.valueOf(s));
            }
        }

        return serialSet;
    }

    private Set<String> splitKeys(String activationKeyString) {
        Set<String> keys = new LinkedHashSet<>();
        if (activationKeyString != null) {
            Collections.addAll(keys, activationKeyString.split(","));
        }
        return keys;
    }

    @Wrapped(element = "serials")
    @UpdateConsumerCheckIn
    public List<CertificateSerialDTO> getEntitlementCertificateSerials(
        @Verify(Consumer.class) String consumerUuid) {
        log.debug("Getting client certificate serials for consumer: {}", consumerUuid);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        revokeOnGuestMigration(consumer);
        poolManager.regenerateDirtyEntitlements(consumer);

        List<CertificateSerialDTO> allCerts = new LinkedList<>();
        for (Long id : entCertService.listEntitlementSerialIds(consumer)) {
            allCerts.add(new CertificateSerialDTO().serial(id));
        }

        // add content access cert if needed
        ContentAccessCertificate cac = this.contentAccessManager.getCertificate(consumer);
        if (cac != null) {
            allCerts.add(new CertificateSerialDTO().serial(cac.getSerial().getId()));
        }

        return allCerts;
    }

    private void validateBindArguments(String poolIdString, Integer quantity, Collection<String> productIds,
        Collection<String> fromPools, Date entitleDate, Consumer consumer, boolean async) {

        short parameters = 0;

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        if (poolIdString != null) {
            parameters++;
        }

        if (CollectionUtils.isNotEmpty(productIds) || CollectionUtils.isNotEmpty(fromPools) ||
            entitleDate != null) {
            parameters++;
        }

        if (parameters > 1) {
            throw new BadRequestException(i18n.tr("Cannot bind by multiple parameters."));
        }

        if (poolIdString == null && quantity != null) {
            throw new BadRequestException(i18n.tr("Cannot specify a quantity when auto-binding."));
        }

    }

    @Override
    @SuppressWarnings({"checkstyle:indentation", "checkstyle:methodlength"})
    public Response bind(
        @Verify(Consumer.class) String consumerUuid,
        @Verify(value = Pool.class, nullable = true, subResource = SubResource.ENTITLEMENTS)
        String poolIdString,
        List<String> productIds,
        Integer quantity,
        String email,
        String emailLocale,
        Boolean async,
        OffsetDateTime entitleDateStr,
        List<String> fromPools) {
        /* NOTE: This method should NEVER be provided with a POST body.
           While technically that change would be backwards compatible,
           there are older clients which erroneously provide an empty string
           as a post body and hence result in a serialization error.
           ref: BZ: 1502807
         */

        Date entitleDate = Util.toDate(entitleDateStr);

        // Verify consumer exists:
        Consumer consumer = consumerCurator.verifyAndLookupConsumerWithEntitlements(consumerUuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        log.debug("Consumer (post verify): {}", consumer);

        // Check that only one query param was set, and some other validations
        validateBindArguments(poolIdString, quantity, productIds, fromPools,
            entitleDate, consumer, async);

        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());

        try {
            // I hate double negatives, but if they have accepted all
            // terms, we want comeToTerms to be true.
            long subTermsStart = System.currentTimeMillis();

            if (subAdapter.hasUnacceptedSubscriptionTerms(owner.getKey())) {
                return Response.serverError().build();
            }

            log.debug("Checked if consumer has unaccepted subscription terms in {}ms",
                (System.currentTimeMillis() - subTermsStart));
        }
        catch (CandlepinException e) {
            log.debug(e.getMessage(), e);
            throw e;
        }

        if (poolIdString != null && quantity == null) {
            Pool pool = poolManager.get(poolIdString);
            quantity = pool != null ? consumerBindUtil.getQuantityToBind(pool, consumer) : 1;
        }

        //
        // HANDLE ASYNC
        //
        if (async) {
            JobConfig jobConfig;

            if (poolIdString != null) {
                String cfg = ConfigProperties.jobConfig(EntitlerJob.JOB_KEY, EntitlerJob.CFG_JOB_THROTTLE);
                int throttle = config.getInt(cfg);

                jobConfig = EntitlerJob.createConfig(throttle)
                    .setOwner(owner)
                    .setConsumer(consumer)
                    .setPoolQuantity(poolIdString, quantity);
            }
            else {
                jobConfig = EntitleByProductsJob.createConfig()
                    .setOwner(owner)
                    .setConsumer(consumer)
                    .setProductIds(productIds)
                    .setEntitleDate(entitleDate)
                    .setPools(fromPools);
            }

            // events will be triggered by the job
            AsyncJobStatus status = null;

            try {
                status = jobManager.queueJob(jobConfig);
            }
            catch (JobException e) {
                String errmsg = this.i18n.tr("An unexpected exception occurred " +
                    "while scheduling job \"{0}\"", jobConfig.getJobKey());
                log.error(errmsg, e);
                throw new IseException(errmsg, e);
            }

            AsyncJobStatusDTO statusDTO = this.translator.translate(status, AsyncJobStatusDTO.class);
            return Response.status(Response.Status.OK)
                .type(MediaType.APPLICATION_JSON)
                .entity(statusDTO)
                .build();
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
                AutobindData autobindData = new AutobindData(consumer, owner)
                    .on(entitleDate)
                    .forProducts(productIds)
                    .withPools(fromPools);

                entitlements = entitler.bindByProducts(autobindData);
            }
            catch (AutobindDisabledForOwnerException e) {
                if (owner.isUsingSimpleContentAccess()) {
                    log.debug("Ignoring request to auto-attach. " +
                        "Attaching subscriptions is disabled for org \"{}\" " +
                        "because simple content access is enabled."
                        , owner.getKey(), e);
                    return Response.status(Response.Status.OK).build();
                }
                else {
                    throw new BadRequestException(i18n.tr("Ignoring request to auto-attach. " +
                        "It is disabled for org \"{0}\"."
                        , owner.getKey()), e);
                }
            }
            catch (AutobindHypervisorDisabledException e) {
                throw new BadRequestException(i18n.tr("Ignoring request to auto-attach. " +
                        "It is disabled for org \"{0}\" because of the hypervisor autobind setting."
                    , owner.getKey()), e);
            }
        }

        List<EntitlementDTO> entitlementDTOs = null;
        if (entitlements != null) {
            entitlementDTOs = new ArrayList<>();

            for (Entitlement ent : entitlements) {
                // we need to supply the compliance type for the pools
                // the method in this class does not do quantity
                addCalculatedAttributes(ent);

                entitlementDTOs.add(this.translator.translate(ent, EntitlementDTO.class));
            }
        }

        // Trigger events:
        entitler.sendEvents(entitlements);

        return Response.status(Response.Status.OK)
            .type(MediaType.APPLICATION_JSON).entity(entitlementDTOs).build();
    }

    @Override
    public List<PoolQuantityDTO> dryBind(@Verify(Consumer.class) String consumerUuid, String serviceLevel) {
        // Verify consumer exists:
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());

        if (owner.isAutobindDisabled()) {
            String message = "";

            if (owner.isUsingSimpleContentAccess()) {
                log.debug("Organization \"{}\" has auto-attach disabled because " +
                    "simple content access is enabled.", owner.getKey());
                return Collections.emptyList();
            }
            else {
                message = (i18n.tr("Organization \"{0}\" has auto-attach disabled.", owner.getKey()));
                throw new BadRequestException(message);
            }
        }

        List<PoolQuantity> dryRunPools = new ArrayList<>();

        try {
            // BZ 1618398 Remove validation check on consumer service level
            // consumerBindUtil.validateServiceLevel(consumer.getOwnerId(), serviceLevel);
            dryRunPools = entitler.getDryRun(consumer, owner, serviceLevel);
        }
        catch (ForbiddenException e) {
            return Collections.emptyList();
        }
        catch (BadRequestException e) {
            throw e;
        }
        catch (RuntimeException e) {
            log.debug("Unexpected exception occurred while performing dry-run:", e);
            return Collections.emptyList();
        }

        if (dryRunPools != null) {
            List<PoolQuantityDTO> dryRunPoolDtos = new ArrayList<>();
            for (PoolQuantity pq : dryRunPools) {
                dryRunPoolDtos.add(this.translator.translate(pq, PoolQuantityDTO.class));
            }

            return dryRunPoolDtos;
        }
        else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<EntitlementDTO> listEntitlements(
        @Verify(Consumer.class) String consumerUuid,
        String productId,
        Boolean regen,
        List<String> attrFilters,
        Integer page, Integer perPage, String order, String sortBy) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        if (regen) {
            revokeOnGuestMigration(consumer);
        }

        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();

        new KeyValueStringParser(this.i18n).parseKeyValuePairs(attrFilters)
            .forEach(kvpair -> filters.addAttributeFilter(kvpair.getKey(), kvpair.getValue()));

        Page<List<Entitlement>> entitlementsPage = entitlementCurator.listByConsumer(consumer, productId,
            filters, pageRequest);

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyContext.pushContext(Page.class, entitlementsPage);

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

        List<EntitlementDTO> entitlementDTOs = new ArrayList<>();
        for (Entitlement entitlement : entitlementsPage.getPageData()) {
            entitlementDTOs.add(this.translator.translate(entitlement, EntitlementDTO.class));
        }

        return entitlementDTOs;
    }

    @Override
    public OwnerDTO getOwnerByConsumerUuid(@Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());
        return translator.translate(owner, OwnerDTO.class);
    }

    @Override
    public DeleteResult unbindAll(@Verify(Consumer.class) String consumerUuid) {

        // FIXME: just a stub, needs CertifcateService (and/or a
        // CertificateCurator) to lookup by serialNumber
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        int total = poolManager.revokeAllEntitlements(consumer, true);
        log.debug("Revoked {} entitlements from {}", total, consumerUuid);
        DeleteResult dr = new DeleteResult();
        dr.setDeletedRecords(total);
        return dr;

        // Need to parse off the value of subscriptionNumberArgs, probably
        // use comma separated see IntergerList in sparklines example in
        // jersey examples find all entitlements for this consumer and
        // subscription numbers delete all of those (and/or return them to
        // entitlement pool)
    }

    @Override
    public void unbindByEntitlementId(@Verify(Consumer.class) String consumerUuid,
        @Verify(Entitlement.class) String dbid) {
        Principal principal = this.principalProvider.get();
        consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Entitlement toDelete = entitlementCurator.get(dbid);
        if (toDelete != null) {
            poolManager.revokeEntitlement(toDelete);
            return;
        }

        throw new NotFoundException(i18n.tr(
            "Entitlement with ID \"{0}\" could not be found.", dbid
        ));
    }

    @Override
    public void unbindBySerial(@Verify(Consumer.class) String consumerUuid, Long serial) {
        consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Entitlement toDelete = entitlementCurator
            .findByCertificateSerial(serial);

        if (toDelete != null) {
            poolManager.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(i18n.tr(
            "Entitlement Certificate with serial number \"{0}\" could not be found.",
            serial.toString())); // prevent serial number formatting.
    }

    @Override
    public void unbindByPool(@Verify(Consumer.class) String consumerUuid, String poolId) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        List<Entitlement> entitlementsToDelete = entitlementCurator
            .listByConsumerAndPoolId(consumer, poolId);
        if (!entitlementsToDelete.isEmpty()) {
            for (Entitlement toDelete : entitlementsToDelete) {
                poolManager.revokeEntitlement(toDelete);
            }
        }
        else {
            throw new NotFoundException(i18n.tr(
                "No entitlements for consumer \"{0}\" with pool id \"{1}\"", consumerUuid, poolId));
        }
    }

    private Entitlement verifyAndLookupEntitlement(String entitlementId) {
        Entitlement entitlement = entitlementCurator.get(entitlementId);
        if (entitlement == null) {
            throw new NotFoundException(i18n.tr("Entitlement with ID \"{0}\" could not be found.",
                entitlementId));
        }

        return entitlement;
    }

    @Override
    @UpdateConsumerCheckIn
    public void regenerateEntitlementCertificates(@Verify(Consumer.class) String consumerUuid,
        String entitlementId, Boolean lazyRegen, Boolean cleanupEntitlements) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        // Normalize boxed flags to avoid any NPEs during unboxing
        lazyRegen = lazyRegen == null || lazyRegen;
        cleanupEntitlements = cleanupEntitlements != null && cleanupEntitlements;

        // If consumer is in an org running in SCA mode and the client has requested entitlement cleanup,
        // revoke all entitlements. They won't be needing them anymore, apparently.
        if (cleanupEntitlements && consumer.getOwner().isUsingSimpleContentAccess()) {
            // At the time of writing, we should only be doing this cleanup in SCA mode, which
            // omits consumer status calculation anyway. As a result, we can save some time by
            // skipping the recalculation during revocation.
            log.info("Cleaning up entitlements for consumer: {}", consumer);
            this.poolManager.revokeAllEntitlements(consumer, false);
        }

        if (entitlementId != null) {
            // TODO: This should probably verify that the consumer in the path is related to it,
            // otherwise this belongs in another endpoint.
            Entitlement entitlement = this.verifyAndLookupEntitlement(entitlementId);
            this.entCertGenerator.regenerateCertificatesOf(entitlement, lazyRegen);
        }
        else {
            this.entCertGenerator.regenerateCertificatesOf(consumer, lazyRegen);
        }
    }

    /**
     * Retrieves a compressed file representation of a Consumer (manifest).
     *
     * @deprecated use GET /consumers/:consumer_uuid/export/async
     * @param consumerUuid
     * @param cdnLabel
     * @param webAppPrefix
     * @param apiUrl
     * @return the generated file archive.
     */
    @Deprecated
    @Override
    public File exportData(@Verify(Consumer.class) String consumerUuid, String cdnLabel, String webAppPrefix,
        String apiUrl) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        HttpServletResponse response = ResteasyContext.getContextData(HttpServletResponse.class);
        try {
            File archive = manifestManager.generateManifest(consumerUuid, cdnLabel, webAppPrefix, apiUrl);
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
     * @param consumerUuid the uuid of the target consumer.
     * @param cdnLabel the CDN label to store in the meta file.
     * @param webAppPrefix the URL pointing to the manifest's originating web application.
     * @param apiUrl the API URL pointing to the manifest's originating candlepin API.
     * @return the details of the async export job that is to be started.
     */
    @Override
    public AsyncJobStatusDTO exportDataAsync(@Verify(Consumer.class) String consumerUuid,
        String cdnLabel, String webAppPrefix, String apiUrl) {
        HttpServletResponse response = ResteasyContext.getContextData(HttpServletResponse.class);
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());

        JobConfig config = manifestManager.generateManifestAsync(consumerUuid, owner, cdnLabel,
            webAppPrefix, apiUrl);

        AsyncJobStatus job = null;

        try {
            job = this.jobManager.queueJob(config);
        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred " +
                "while scheduling job \"{0}\"", config.getJobKey());
            log.error(errmsg, e);
            throw new IseException(errmsg, e);
        }

        return this.translator.translate(job, AsyncJobStatusDTO.class);
    }

    /**
     * Downloads an asynchronously generated consumer export file (manifest). If the file
     * was successfully downloaded, it will be deleted.
     *
     * @param consumerUuid the UUID of the target consumer.
     * @param exportId the id of the stored export.
     */
    @Override
    public File downloadExistingExport(@Verify(Consumer.class) String consumerUuid, String exportId) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        HttpServletResponse response = ResteasyContext.getContextData(HttpServletResponse.class);
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

        // Done intentionally due to OpenAPI constrains on return type.
        return null;
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
     * Retrieves a single Consumer & regenerate Identity Certificates
     *
     * @param uuid uuid of the consumer sought.
     * @return a Consumer object
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @Override
    public ConsumerDTO regenerateIdentityCertificates(@Verify(Consumer.class) String uuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        consumer = regenerateIdentityCertificate(consumer);
        return translator.translate(consumer, ConsumerDTO.class);
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
            .setEventData(consumer);

        IdentityCertificate ic = generateIdCert(consumer, true);
        consumer.setIdCert(ic);
        consumerCurator.update(consumer);
        sink.queueEvent(eventBuilder.setEventData(consumer).buildEvent());
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

        try {
            idCert = regen ?
                identityCertService.regenerateIdentityCert(c) :
                identityCertService.generateIdentityCert(c);

            if (idCert == null) {
                throw new BadRequestException(i18n.tr("Problem regenerating ID cert for unit {0}", c));
            }
        }
        catch (GeneralSecurityException | IOException e) {
            log.error("Problem regenerating ID cert for unit:", e);
            throw new BadRequestException(i18n.tr("Problem regenerating ID cert for unit {0}", c), e);
        }

        log.debug("Generated identity cert: {}", idCert.getSerial());
        return idCert;
    }

    @Override
    public List<ConsumerDTOArrayElement> getGuests(@Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        List<Consumer> consumers = consumerCurator.getGuests(consumer);
        return translate(consumers);
    }

    private List<ConsumerDTOArrayElement> translate(List<Consumer> consumers) {
        if (consumers != null) {
            List<ConsumerDTOArrayElement> results = new LinkedList<>();
            for (Consumer consumer : consumers) {
                results.add(translator.translate(consumer, ConsumerDTOArrayElement.class));
            }
            return results;
        }
        else {
            return null;
        }
    }

    @Override
    public ConsumerDTO getHost(@Verify(Consumer.class) String consumerUuid) {
        Principal principal = this.principalProvider.get();
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        if (consumer.getFact(Consumer.Facts.VIRT_UUID) == null ||
            consumer.getFact(Consumer.Facts.VIRT_UUID).trim().equals("")) {
            throw new BadRequestException(i18n.tr("The system with UUID {0} is not a virtual guest.",
                consumer.getUuid()));
        }

        Consumer host = consumerCurator.getHost(consumer.getFact(Consumer.Facts.VIRT_UUID),
            consumer.getOwnerId());

        return translator.translate(host, ConsumerDTO.class);
    }

    @Override
    public ReleaseVerDTO getRelease(@Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        ReleaseVerDTO release = new ReleaseVerDTO();

        if (consumer.getReleaseVer() != null) {
            return release.releaseVer(consumer.getReleaseVer().getReleaseVer());
        }
        else {
            release.setReleaseVer("");
        }

        return release;
    }

    @Override
    @Transactional
    public ComplianceStatusDTO getComplianceStatus(@Verify(Consumer.class) String uuid,
        OffsetDateTime onDate) {
        ComplianceStatus status = null;
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        Date date = Util.toDate(onDate);
        status = this.complianceRules.getStatus(consumer, date);

        return this.translator.translate(status, ComplianceStatusDTO.class);
    }

    @Override
    @Transactional
    public SystemPurposeComplianceStatusDTO getSystemPurposeComplianceStatus(
        @Verify(Consumer.class) String uuid, OffsetDateTime onDate) {
        SystemPurposeComplianceStatus status = null;
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);
        Date date = Util.toDate(onDate);
        status = this.systemPurposeComplianceRules.getStatus(consumer, consumer.getEntitlements(), null,
            date, true);

        return this.translator.translate(status, SystemPurposeComplianceStatusDTO.class);
    }

    @Override
    @Transactional
    public Map<String, ComplianceStatusDTO> getComplianceStatusList(
        @Verify(value = Consumer.class, nullable = true) List<String> uuids) {

        Map<String, ComplianceStatusDTO> results = new HashMap<>();

        if (uuids != null && !uuids.isEmpty()) {
            for (Consumer consumer : consumerCurator.findByUuids(uuids)) {
                ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
                ComplianceStatus status;

                status = complianceRules.getStatus(consumer, null);

                results.put(consumer.getUuid(), this.translator.translate(status, ComplianceStatusDTO.class));
            }
        }

        return results;
    }

    @Transactional
    public void removeDeletionRecord(String uuid) {
        DeletedConsumer dc = deletedConsumerCurator.findByConsumerUuid(uuid);
        if (dc == null) {
            throw new NotFoundException(
                i18n.tr("Deletion record for hypervisor \"{0}\" not found.", uuid));
        }

        deletedConsumerCurator.delete(dc);
    }


    private void addCalculatedAttributes(Entitlement ent) {
        // With no consumer/date, this will not build suggested quantity
        Map<String, String> calculatedAttributes =
            calculatedAttributesUtil.buildCalculatedAttributes(ent.getPool(), null);
        ent.getPool().setCalculatedAttributes(calculatedAttributes);
    }

    public String getFactValue(Map<String, String> facts, String factsKey) {
        if (facts != null) {
            return facts.get(factsKey);
        }
        return null;
    }



    /**
     * Populates the specified entities with data from the provided guestIds.
     *
     * @param entities
     *  The entities instance to populate
     *
     * @param guestIds
     *  The list of string containing the guestIds to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    protected void populateEntities(List<GuestId> entities, List<String> guestIds) {
        if (entities == null) {
            throw new IllegalArgumentException("the guestId model entity is null");
        }

        if (guestIds == null) {
            throw new IllegalArgumentException("the list of guestId is null");
        }

        for (String guestId : guestIds) {
            if (guestId == null) {
                continue;
            }
            entities.add(new GuestId(guestId));
        }
    }
}
