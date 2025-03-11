/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.ConsumerMigrationJob;
import org.candlepin.async.tasks.HealEntireOrgJob;
import org.candlepin.async.tasks.RefreshPoolsJob;
import org.candlepin.async.tasks.UndoImportsJob;
import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.ConsumerManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentAccessMode;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerContentAccess;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ActivationKeyDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.ClaimantOwner;
import org.candlepin.dto.api.server.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.server.v1.ContentAccessDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.dto.api.server.v1.EntitlementDTO;
import org.candlepin.dto.api.server.v1.EnvironmentDTO;
import org.candlepin.dto.api.server.v1.ImportRecordDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.OwnerInfo;
import org.candlepin.dto.api.server.v1.PoolDTO;
import org.candlepin.dto.api.server.v1.SetConsumerEnvironmentsDTO;
import org.candlepin.dto.api.server.v1.SubscriptionDTO;
import org.candlepin.dto.api.server.v1.SystemPurposeAttributesDTO;
import org.candlepin.dto.api.server.v1.UeberCertificateDTO;
import org.candlepin.dto.api.server.v1.UpstreamConsumerDTOArrayElement;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerCurator.ConsumerQueryArguments;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.InvalidOrderKeyException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerCurator.OwnerQueryArguments;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.OwnerNotFoundException;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQualifier;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Release;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.SystemPurposeAttributeType;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.pki.certs.UeberCertificateGenerator;
import org.candlepin.resource.server.v1.OwnerApi;
import org.candlepin.resource.util.AttachedFile;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.KeyValueStringParser;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.file.ManifestFileServiceException;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.NonNullLinkedHashSet;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.Util;

import ch.qos.logback.classic.Level;

import com.google.inject.persist.Transactional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.persistence.PersistenceException;

/**
 * Owner Resource
 */
public class OwnerResource implements OwnerApi {
    private static final Logger log = LoggerFactory.getLogger(OwnerResource.class);
    private static final Pattern AK_CHAR_FILTER = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final OwnerCurator ownerCurator;
    private final OwnerInfoCurator ownerInfoCurator;
    private final ActivationKeyCurator activationKeyCurator;
    private final OwnerServiceAdapter ownerService;
    private final ConsumerCurator consumerCurator;
    private final ConsumerManager consumerManager;
    private final I18n i18n;
    private final EventSink sink;
    private final EventFactory eventFactory;
    private final ManifestManager manifestManager;
    private final ExporterMetadataCurator exportCurator;
    private final ImportRecordCurator importRecordCurator;
    private final ContentAccessManager contentAccessManager;
    private final PoolManager poolManager;
    private final PoolService poolService;
    private final PoolCurator poolCurator;
    private final OwnerManager ownerManager;
    private final EntitlementCurator entitlementCurator;
    private final UeberCertificateCurator ueberCertCurator;
    private final UeberCertificateGenerator ueberCertGenerator;
    private final EnvironmentCurator envCurator;
    private final CalculatedAttributesUtil calculatedAttributesUtil;
    private final ContentOverrideValidator contentOverrideValidator;
    private final ServiceLevelValidator serviceLevelValidator;
    private final Configuration config;
    private final ConsumerTypeValidator consumerTypeValidator;
    private final ProductCurator productCurator;
    private final ModelTranslator translator;
    private final JobManager jobManager;
    private final DTOValidator validator;
    private final PrincipalProvider principalProvider;
    private final PagingUtilFactory pagingUtilFactory;
    private final int maxPagingSize;

    @Inject
    @SuppressWarnings("checkstyle:parameternumber")
    public OwnerResource(OwnerCurator ownerCurator,
        ActivationKeyCurator activationKeyCurator,
        ConsumerCurator consumerCurator,
        ConsumerManager consumerManager,
        I18n i18n,
        EventSink sink,
        EventFactory eventFactory,
        ContentAccessManager contentAccessManager,
        ManifestManager manifestManager,
        PoolManager poolManager,
        PoolService poolService,
        PoolCurator poolCurator,
        OwnerManager ownerManager,
        ExporterMetadataCurator exportCurator,
        OwnerInfoCurator ownerInfoCurator,
        ImportRecordCurator importRecordCurator,
        EntitlementCurator entitlementCurator,
        UeberCertificateCurator ueberCertCurator,
        UeberCertificateGenerator ueberCertGenerator,
        EnvironmentCurator envCurator,
        CalculatedAttributesUtil calculatedAttributesUtil,
        ContentOverrideValidator contentOverrideValidator,
        ServiceLevelValidator serviceLevelValidator,
        OwnerServiceAdapter ownerService,
        Configuration config,
        ConsumerTypeValidator consumerTypeValidator,
        ProductCurator productCurator,
        ModelTranslator translator,
        JobManager jobManager,
        DTOValidator validator,
        PrincipalProvider principalProvider,
        PagingUtilFactory pagingUtilFactory) {

        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.ownerInfoCurator = Objects.requireNonNull(ownerInfoCurator);
        this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerManager = Objects.requireNonNull(consumerManager);
        this.i18n = Objects.requireNonNull(i18n);
        this.sink = Objects.requireNonNull(sink);
        this.eventFactory = Objects.requireNonNull(eventFactory);
        this.exportCurator = Objects.requireNonNull(exportCurator);
        this.importRecordCurator = Objects.requireNonNull(importRecordCurator);
        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.poolService = Objects.requireNonNull(poolService);
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.manifestManager = Objects.requireNonNull(manifestManager);
        this.ownerManager = Objects.requireNonNull(ownerManager);
        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.ueberCertCurator = Objects.requireNonNull(ueberCertCurator);
        this.ueberCertGenerator = Objects.requireNonNull(ueberCertGenerator);
        this.envCurator = Objects.requireNonNull(envCurator);
        this.calculatedAttributesUtil = Objects.requireNonNull(calculatedAttributesUtil);
        this.contentOverrideValidator = Objects.requireNonNull(contentOverrideValidator);
        this.serviceLevelValidator = Objects.requireNonNull(serviceLevelValidator);
        this.ownerService = Objects.requireNonNull(ownerService);
        this.config = Objects.requireNonNull(config);
        this.consumerTypeValidator = Objects.requireNonNull(consumerTypeValidator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.validator = Objects.requireNonNull(validator);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.pagingUtilFactory = Objects.requireNonNull(pagingUtilFactory);
        this.maxPagingSize = this.config.getInt(ConfigProperties.PAGING_MAX_PAGE_SIZE);
    }

    /**
     * Returns the owner object that is identified by the given key, if it is found in the system.
     * Otherwise, it throws a NotFoundException.
     *
     * @param key the key that is associated with the owner we are searching for.
     *
     * @return the owner that was found in the system based on the given key.
     *
     * @throws NotFoundException
     *  if the owner with the given key was not found in the system.
     *
     * @throws BadRequestException
     *  if the given Owner key is null or empty.
     */
    private Owner findOwnerByKey(String key) {
        Owner owner;
        if (key != null && !key.isEmpty()) {
            owner = ownerCurator.getByKey(key);
        }
        else {
            throw new BadRequestException(i18n.tr("Owner key is null or empty."));
        }

        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found", key));
        }

        return owner;
    }

    /**
     * Returns the owner object that is identified by the given id, if it is found in the system.
     * Otherwise, it throws a NotFoundException.
     *
     * @param id the id of the owner we are searching for.
     *
     * @return the owner that was found in the system based on the given id.
     *
     * @throws NotFoundException
     *  if the owner with the given id was not found in the system.
     *
     * @throws BadRequestException
     *  if the given Owner id is null or empty.
     */
    private Owner findOwnerById(String id) {
        Owner owner = null;
        if (id != null && !id.isEmpty()) {
            owner = ownerCurator.get(id);
        }
        else {
            throw new BadRequestException(i18n.tr("Owner id is null or empty."));
        }

        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with id \"{0}\" was not found", id));
        }

        return owner;
    }

    /**
     * Returns the owner object that is identified by the given id or key, if it is found in the system.
     * A search with the id has priority. If the id is null, only then a search with the key is performed.
     * If both the id and the key are null, a {@link BadRequestException} is thrown.
     *
     * @param id the id of the owner we are searching for.
     *
     * @param key the key that is associated with the owner we are searching for.
     *
     * @return the owner that was found in the system based on the given id or key.
     *
     * @throws BadRequestException
     *  If both the id and the key are null or empty.
     */
    private Owner findOwnerByIdOrKey(String id, String key) {
        if (id != null && !id.isEmpty()) {
            return this.findOwnerById(id);
        }
        else if (key != null && !key.isEmpty()) {
            return this.findOwnerByKey(key);
        }
        else {
            throw new BadRequestException(i18n.tr(
                "Owner lookup failed because id and key were null or empty."));
        }
    }

    /**
     * Returns the entitlement object that is identified by the given id, if it is found in the system.
     * Otherwise, it throws a NotFoundException.
     *
     * @param entitlementId the id of the entitlement we are searching for.
     *
     * @return the entitlement that was found in the system based on the given id.
     *
     * @throws NotFoundException
     *  if the entitlement with the given id was not found in the system.
     *
     * @throws BadRequestException
     *  if the given Entitlement id is null or empty.
     */
    private Entitlement findEntitlement(String entitlementId) {
        Entitlement entitlement = null;
        if (entitlementId != null && !entitlementId.isEmpty()) {
            entitlement = entitlementCurator.get(entitlementId);
        }
        else {
            throw new BadRequestException(i18n.tr("Entitlement id is null or empty."));
        }

        if (entitlement == null) {
            throw new NotFoundException(
                i18n.tr("Entitlement with id {0} could not be found.", entitlementId));
        }

        return entitlement;
    }

    /**
     * Returns the consumer object that is identified by the given uuid, if it is found in the system.
     * Otherwise, it throws a NotFoundException.
     *
     * @param consumerUuid the uuid of the consumer we are searching for.
     *
     * @return the consumer that was found in the system based on the given uuid.
     *
     * @throws NotFoundException
     *  if the consumer with the given uuid was not found in the system.
     *
     * @throws BadRequestException
     *  if the given Consumer id is null or empty.
     */
    private Consumer findConsumer(String consumerUuid) {
        Consumer consumer;
        if (consumerUuid != null && !consumerUuid.isEmpty()) {
            consumer = consumerCurator.findByUuid(consumerUuid);
        }
        else {
            throw new BadRequestException(i18n.tr("Consumer id is null or empty."));
        }

        if (consumer == null) {
            throw new NotFoundException(i18n.tr("No such unit: {0}", consumerUuid));
        }

        return consumer;
    }

    /**
     * Returns the pool object that is identified by the given id, if it is found in the system.
     * Otherwise, it throws a NotFoundException.
     *
     * @param poolId the id of the pool we are searching for.
     *
     * @return the pool that was found in the system based on the given id.
     *
     * @throws NotFoundException
     *  if the pool with the given id was not found in the system.
     *
     * @throws BadRequestException
     *  if the given Pool id is null or empty.
     */
    private Pool findPool(String poolId) {
        Pool pool;
        if (poolId != null && !poolId.isEmpty()) {
            pool = this.poolService.get(poolId);
        }
        else {
            throw new BadRequestException(i18n.tr("Pool id is null or empty."));
        }

        if (pool == null) {
            throw new BadRequestException(i18n.tr("Pool with id {0} could not be found.", poolId));
        }

        return pool;
    }

    /**
     * Attempts to resolve the given product ID reference to a product for the given organization.
     * This method will first attempt to resolve the product reference in the org's namespace, but
     * will fall back to the global namespace if that resolution attempt fails. If the product ID
     * cannot be resolved, this method throws an exception.
     *
     * @param owner
     *  the organization for which to resolve the product reference
     *
     * @param productId
     *  the product ID to resolve
     *
     * @throws IllegalArgumentException
     *  if owner is null, or if productId is null or empty
     *
     * @throws NotFoundException
     *  if a product with the specified ID cannot be found within the context of the given org
     *
     * @return
     *  the product for the specified ID
     */
    private Product resolveProductId(Owner owner, String productId) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        Product product = this.productCurator.resolveProductId(owner.getKey(), productId);

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find a product with the ID \"{0}\" for owner \"{1}\"",
                    product, owner.getKey()));
        }

        return product;
    }

    /**
     * Populates the specified entity with data from the provided DTO. This method will not set the
     * ID, key, upstream consumer, content access mode list or content access mode fields.
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
    private void populateEntity(Owner entity, OwnerDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the owner model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the owner dto is null");
        }

        if (dto.getDisplayName() != null) {
            entity.setDisplayName(dto.getDisplayName());
        }

        if (dto.getParentOwner() != null) {
            // Impl note:
            // We do not allow modifying a parent owner through its children, so all we'll do here
            // is set the parent owner and ignore everything else; including further nested owners.

            NestedOwnerDTO pdto = dto.getParentOwner();
            Owner parent = null;

            if (pdto.getId() != null) {
                // look up by ID
                parent = this.ownerCurator.get(pdto.getId());
            }
            else if (pdto.getKey() != null) {
                // look up by key
                parent = this.ownerCurator.getByKey(pdto.getKey());
            }

            if (parent == null) {
                throw new NotFoundException(i18n.tr("Unable to find parent owner: {0}", pdto));
            }

            entity.setParentOwner(parent);
        }

        if (dto.getContentPrefix() != null) {
            entity.setContentPrefix(dto.getContentPrefix());
        }

        if (dto.getDefaultServiceLevel() != null) {
            if (dto.getDefaultServiceLevel().isEmpty()) {
                entity.setDefaultServiceLevel(null);
            }
            else {
                this.serviceLevelValidator.validate(entity.getId(), dto.getDefaultServiceLevel());
                entity.setDefaultServiceLevel(dto.getDefaultServiceLevel());
            }
        }

        if (dto.getLogLevel() != null) {
            entity.setLogLevel(dto.getLogLevel());
        }

        if (dto.getAutobindDisabled() != null) {
            entity.setAutobindDisabled(dto.getAutobindDisabled());
        }

        if (dto.getAutobindHypervisorDisabled() != null) {
            entity.setAutobindHypervisorDisabled(dto.getAutobindHypervisorDisabled());
        }

        // Note: Do not set the content access mode list or content access mode here. Those should
        // be done through ContentAccessManager.updateOwnerContentAccess instead.
    }

    /**
     * Populates the specified entity with data from the provided DTO. This method will not set the
     * ID field.
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
    private void populateEntity(ActivationKey entity, ActivationKeyDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the activation key model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the activation key dto is null");
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }

        if (dto.getAutoAttach() != null) {
            entity.setAutoAttach(dto.getAutoAttach());
        }

        if (dto.getServiceLevel() != null) {
            entity.setServiceLevel(dto.getServiceLevel());
        }

        if (dto.getOwner() != null) {
            entity.setOwner(lookupOwnerFromDto(dto.getOwner()));
        }

        if (dto.getServiceLevel() != null) {
            if (dto.getServiceLevel().isEmpty()) {
                entity.setServiceLevel(null);
            }
            else {
                entity.setServiceLevel(dto.getServiceLevel());
            }
        }

        if (dto.getContentOverrides() != null) {
            if (dto.getContentOverrides().isEmpty()) {
                entity.setContentOverrides(new HashSet<>());
            }
            else {
                for (ContentOverrideDTO overrideDTO : dto.getContentOverrides()) {
                    if (overrideDTO != null) {
                        entity.addContentOverride(new ActivationKeyContentOverride()
                            .setKey(entity)
                            .setContentLabel(overrideDTO.getContentLabel())
                            .setName(overrideDTO.getName())
                            .setValue(overrideDTO.getValue()));
                    }
                }
            }
        }

        if (dto.getReleaseVer() != null) {
            entity.setReleaseVer(new Release(dto.getReleaseVer().getReleaseVer()));
        }

        if (dto.getUsage() != null) {
            entity.setUsage(dto.getUsage());
        }

        if (dto.getRole() != null) {
            entity.setRole(dto.getRole());
        }

        if (dto.getAddOns() != null) {
            entity.setAddOns(new HashSet<>(dto.getAddOns()));
        }

        if (dto.getPools() != null) {
            if (dto.getPools().isEmpty()) {
                entity.setPools(new HashSet<>());
            }
            else {
                for (ActivationKeyPoolDTO poolDTO : dto.getPools()) {
                    if (poolDTO != null) {
                        Pool pool = findPool(poolDTO.getPoolId());
                        entity.addPool(pool, poolDTO.getQuantity());
                    }
                }
            }
        }

        if (dto.getProducts() != null) {
            Set<String> pids = new HashSet<>();

            // We need to resolve the product references here, and due to how activation key
            // products work, we need not limit them to the org's namespace.
            dto.getProducts().stream()
                .map(keyprod -> this.resolveProductId(entity.getOwner(), keyprod.getProductId()))
                .map(Product::getId)
                .forEach(pids::add);

            entity.setProductIds(pids);
        }
    }

    /*
     * Populates the specified entity with data from the provided DTO.
     * TODO: Remove this method once EnvironmentDTO gets moved to spec-first
     *  and starts using NestedOwnerDTO.
     */

    /*
     * Populates the specified entity with data from the provided DTO.
     */
    private Owner lookupOwnerFromDto(NestedOwnerDTO ownerDto) {
        return this.findOwnerByIdOrKey(ownerDto.getId(), ownerDto.getKey());
    }

    /**
     * Populates the specified entity with data from the provided DTO.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null, or if the dto's environment content is not empty
     */
    private void populateEntity(Environment entity, EnvironmentDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the environment model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the environment dto is null");
        }

        if (CollectionUtils.isNotEmpty(dto.getEnvironmentContent())) {
            throw new IllegalArgumentException("can not specify environment content at creation time");
        }

        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setDescription(dto.getDescription());
        entity.setContentPrefix(dto.getContentPrefix());
        entity.setOwner(lookupOwnerFromDto(dto.getOwner()));
    }

    /**
     * Populates an entity that is to be created with data from the provided DTO.
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
    private void populateEntity(Pool entity, PoolDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getId() != null) {
            entity.setId(dto.getId());
        }

        if (dto.getStartDate() != null) {
            entity.setStartDate(Util.toDate(dto.getStartDate()));
        }

        if (dto.getEndDate() != null) {
            entity.setEndDate(Util.toDate(dto.getEndDate()));
        }

        if (dto.getQuantity() != null) {
            entity.setQuantity(dto.getQuantity());
        }

        if (dto.getAttributes() != null) {
            entity.setAttributes(Util.toMap(dto.getAttributes()));
        }

        // Impl note: derived product, provided products, and derived provided products are no longer
        // imported from the DTO.
    }

    @Override
    @Transactional
    @Wrapped(element = "owners")
    public Stream<OwnerDTO> listOwners(String keyFilter, Integer page, Integer perPage, String order,
        String sortBy) {

        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);

        OwnerQueryArguments queryArgs = new OwnerQueryArguments()
            .setKeys(
                keyFilter != null ?
                Arrays.asList(keyFilter) :
                null);

        long count = this.ownerCurator.getOwnerCount(queryArgs);

        if (pageRequest != null) {
            Page<Stream<OwnerDTO>> pageResponse = new Page<>();
            pageResponse.setPageRequest(pageRequest);

            if (pageRequest.isPaging()) {
                queryArgs.setOffset((pageRequest.getPage() - 1) * pageRequest.getPerPage())
                    .setLimit(pageRequest.getPerPage());
            }

            if (pageRequest.getSortBy() != null) {
                boolean reverse = pageRequest.getOrder() == PageRequest.DEFAULT_ORDER;
                queryArgs.addOrder(pageRequest.getSortBy(), reverse);
            }

            pageResponse.setMaxRecords((int) count);

            // Store the page for the LinkHeaderResponseFilter
            ResteasyContext.pushContext(Page.class, pageResponse);
        }
        // If no paging was specified, force a limit on amount of results
        else {
            int maxSize = config.getInt(ConfigProperties.PAGING_MAX_PAGE_SIZE);
            if (count > maxSize) {
                String errmsg = this.i18n.tr("This endpoint does not support returning more than {0} " +
                    "results at a time, please use paging.", maxSize);
                throw new BadRequestException(errmsg);
            }
        }

        return this.ownerCurator.listAll(queryArgs).stream()
            .map(this.translator.getStreamMapper(Owner.class, OwnerDTO.class));

    }

    @Override
    @Transactional
    public OwnerDTO getOwner(@Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        return this.translator.translate(owner, OwnerDTO.class);
    }

    @Override
    @Transactional
    public ContentAccessDTO getOwnerContentAccess(@Verify(Owner.class) String ownerKey) {
        try {
            OwnerContentAccess owner = this.ownerCurator.getOwnerContentAccess(ownerKey);
            String caMode = Util.firstOf(
                owner.getContentAccessMode(),
                ContentAccessMode.getDefault().toDatabaseValue());
            String caList = Util.firstOf(
                owner.getContentAccessModeList(),
                ContentAccessManager.defaultContentAccessModeList());
            return new ContentAccessDTO()
                .contentAccessMode(caMode)
                .contentAccessModeList(Util.toList(caList));
        }
        catch (OwnerNotFoundException e) {
            throw new NotFoundException("Owner was not found!", e);
        }
    }

    @Override
    @Transactional
    public OwnerInfo getOwnerInfo(
        @Verify(value = Owner.class, subResource = SubResource.CONSUMERS) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        return ownerInfoCurator.getByOwner(owner);
    }

    @Override
    @Transactional
    public OwnerDTO createOwner(OwnerDTO dto) {
        return this.translator.translate(createOwnerFromDTO(dto), OwnerDTO.class);
    }

    @Override
    @Transactional
    public OwnerDTO updateOwner(@Verify(Owner.class) String key,
        OwnerDTO dto) {
        log.debug("Updating owner: {}", key);

        Owner owner = findOwnerByKey(key);

        boolean updateContentAccess = false;
        String contentAccessModeList = dto.getContentAccessModeList();
        String contentAccessMode = dto.getContentAccessMode();

        // Check content access mode bits
        boolean caListChanged = contentAccessModeList != null &&
            !contentAccessModeList.equals(owner.getContentAccessModeList());

        boolean caModeChanged = contentAccessMode != null &&
            !contentAccessMode.equals(owner.getContentAccessMode());

        if (caListChanged || caModeChanged) {
            // This kinda doubles up on some work here, but at least we nice, clear error messages
            // rather than spooky ISEs.
            this.validateContentAccessModeChanges(owner, contentAccessModeList, contentAccessMode);
            updateContentAccess = true;
        }

        // Check if the content prefix changed
        String contentPrefix = dto.getContentPrefix();
        boolean contentPrefixChanged = contentPrefix != null &&
            !contentPrefix.equals(owner.getContentPrefix());

        // Do the bulk of our entity population
        this.populateEntity(owner, dto);

        // If the prefix changed, we should flag certs as dirty and update the last content changed
        // so clients can get their certs regenerated with the new paths
        if (contentPrefixChanged) {
            owner.syncLastContentUpdate();
            this.entitlementCurator.markEntitlementsDirtyForOwner(owner.getId());
        }

        // Refresh content access mode if necessary
        if (updateContentAccess) {
            owner = this.contentAccessManager
                .updateOwnerContentAccess(owner, contentAccessModeList, contentAccessMode);
        }
        else {
            // ContentAccessManager does its own merge on owner,
            // if updates are NOT related to content access we need to take care of it.
            owner = ownerCurator.merge(owner);
            ownerCurator.flush();
        }

        // Build and queue the owner modified event
        Event event = this.eventFactory.getEventBuilder(Target.OWNER, Type.MODIFIED)
            .setEventData(owner)
            .buildEvent();

        this.sink.queueEvent(event);

        // Output the updated owner
        return this.translator.translate(owner, OwnerDTO.class);
    }

    /**
     * Validates that the given content access mode changes can be used by the specified owner.
     *
     * @param owner
     *  the owner for which to check if the content access mode list is valid
     *
     * @param calist
     *  the content access mode list to check
     *
     * @param camode
     *  the content access mode
     *
     * @throws BadRequestException
     *  if the content access mode list is not currently valid for the given owner
     */
    private void validateContentAccessModeChanges(Owner owner, String calist, String camode) {
        if (calist != null) {
            if (calist.isEmpty()) {
                calist = ContentAccessManager.defaultContentAccessModeList();
            }

            String[] modes = calist.split(",");
            for (String mode : modes) {
                try {
                    ContentAccessMode.resolveModeName(mode);
                }
                catch (IllegalArgumentException e) {
                    throw new BadRequestException(this.i18n.tr("Content access mode list contains " +
                        "an unsupported mode: {0}", mode), e);
                }
            }
        }
        else {
            calist = owner.getContentAccessModeList();
        }

        if (camode != null) {
            if (camode.isEmpty()) {
                camode = ContentAccessMode.getDefault().toDatabaseValue();
            }

            if (calist == null || calist.isEmpty()) {
                calist = ContentAccessManager.defaultContentAccessModeList();
            }

            String[] modes = calist.split(",");
            if (!ArrayUtils.contains(modes, camode)) {
                throw new BadRequestException(this.i18n.tr("Content access mode is not present " +
                    "in the content access mode list for this org: {0}", camode));
            }
        }
    }

    @Override
    @Transactional
    public void deleteOwner(String ownerKey,
        Boolean revoke, Boolean force) {

        Owner owner = findOwnerByKey(ownerKey);
        Event event = eventFactory.ownerDeleted(owner);

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

    @Override
    @Transactional
    public List<EntitlementDTO> ownerEntitlements(
        @Verify(Owner.class) String ownerKey,
        String productId,
        List<String> attrFilters,
        Integer page, Integer perPage, String order, String sortBy) {
        Owner owner = findOwnerByKey(ownerKey);
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);

        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();

        new KeyValueStringParser(this.i18n).parseKeyValuePairs(attrFilters)
            .forEach(kvpair -> filters.addAttributeFilter(kvpair.getKey(), kvpair.getValue()));

        Page<List<Entitlement>> entitlementsPage = entitlementCurator
            .listByOwner(owner, productId, filters, pageRequest);

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyContext.pushContext(Page.class, entitlementsPage);

        List<EntitlementDTO> entitlementDTOs = new ArrayList<>();
        for (Entitlement entitlement : entitlementsPage.getPageData()) {
            entitlementDTOs.add(this.translator.translate(entitlement, EntitlementDTO.class));
        }

        return entitlementDTOs;
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO healEntire(@Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        JobConfig config = HealEntireOrgJob.createJobConfig()
            .setOwner(owner)
            .setEntitleDate(new Date());

        return queueJob(config);
    }

    @Override
    @Transactional
    public Set<String> ownerServiceLevels(
        @Verify(value = Owner.class, subResource = SubResource.SERVICE_LEVELS) String ownerKey,
        String exempt) {
        Owner owner = findOwnerByKey(ownerKey);

        // test is on the string "true" and is case insensitive.
        return poolManager.retrieveServiceLevelsForOwner(owner.getId(), Boolean.parseBoolean(exempt));
    }

    @Override
    @Transactional
    public Stream<ActivationKeyDTO> ownerActivationKeys(
        @Verify(value = Owner.class, subResource = SubResource.ACTIVATION_KEYS) String ownerKey,
        String keyName) {

        Owner owner = findOwnerByKey(ownerKey);

        List<ActivationKey> keys = this.activationKeyCurator.listByOwner(owner, keyName);
        return keys.stream()
            .map(this.translator.getStreamMapper(ActivationKey.class, ActivationKeyDTO.class));
    }

    @Override
    @Transactional
    public ActivationKeyDTO createActivationKey(
        @Verify(value = Owner.class, subResource = SubResource.ACTIVATION_KEYS) String ownerKey,
        ActivationKeyDTO dto) {

        validator.validateCollectionElementsNotNull(dto::getContentOverrides, dto::getPools,
            dto::getProducts);

        if (StringUtils.isBlank(dto.getName())) {
            throw new BadRequestException(i18n.tr("Must provide a name for activation key."));
        }

        Matcher keyMatcher = AK_CHAR_FILTER.matcher(dto.getName());

        if (!keyMatcher.matches()) {
            throw new BadRequestException(
                i18n.tr("The activation key name \"{0}\" must be alphanumeric or " +
                    "include the characters \"-\" or \"_\"", dto.getName()));
        }

        if (dto.getContentOverrides() != null) {
            contentOverrideValidator.validate(dto.getContentOverrides());
        }

        Owner owner = findOwnerByKey(ownerKey);

        if (activationKeyCurator.getByKeyName(owner, dto.getName()) != null) {
            throw new BadRequestException(
                i18n.tr("The activation key name \"{0}\" is already in use for owner {1}",
                    dto.getName(), ownerKey));
        }

        // If we're running in SCA mode, don't allow creating keys with pools,
        // because owner should see all the pools anyway
        if (owner.isUsingSimpleContentAccess()) {
            Set<ActivationKeyPoolDTO> pools = dto.getPools();
            if (pools != null && !pools.isEmpty()) {
                throw new BadRequestException(i18n.tr("Activation keys cannot be created with pools while" +
                    " the org is operating in simple content access mode"));
            }
        }

        serviceLevelValidator.validate(owner.getId(), dto.getServiceLevel());

        ActivationKey key = new ActivationKey();
        this.populateEntity(key, dto);
        key.setOwner(owner);

        ActivationKey newKey = activationKeyCurator.create(key);
        sink.emitActivationKeyCreated(newKey);

        return translator.translate(newKey, ActivationKeyDTO.class);
    }

    @Override
    @Transactional
    public EnvironmentDTO createEnvironment(@Verify(Owner.class) String ownerKey, EnvironmentDTO envDTO) {
        Environment env = new Environment();
        NestedOwnerDTO ownerDTO = new NestedOwnerDTO().key(ownerKey);
        envDTO.setOwner(ownerDTO);
        populateEntity(env, envDTO);

        env = envCurator.create(env);
        return translator.translate(env, EnvironmentDTO.class);
    }

    @Override
    @Transactional
    @RootResource.LinkedResource
    public Stream<EnvironmentDTO> listEnvironments(
        @Verify(Owner.class) String ownerKey, String envName, List<String> type, Boolean listAll) {

        Owner owner = this.findOwnerByKey(ownerKey);

        List<Environment> envs = (listAll != null && listAll) ?
            this.envCurator.listAllTypes(owner, envName) :
            this.envCurator.listByType(owner, envName, type == null || type.size() == 0 ? null : type);

        Stream<EnvironmentDTO> stream = envs.stream()
            .map(this.translator.getStreamMapper(Environment.class, EnvironmentDTO.class));

        return this.pagingUtilFactory.forClass(EnvironmentDTO.class)
            .applyPaging(stream, envs.size());
    }

    @Override
    @Transactional
    public OwnerDTO setLogLevel(String ownerKey, String level) {
        Owner owner = findOwnerByKey(ownerKey);

        Level logLevel = Level.toLevel(level, null);
        if (logLevel == null) {
            throw new BadRequestException(i18n.tr("{0} is not a valid log level", level));
        }

        owner.setLogLevel(logLevel.toString());
        owner = ownerCurator.merge(owner);
        this.ownerCurator.flush();

        return this.translator.translate(owner, OwnerDTO.class);
    }

    @Override
    @Transactional
    public void deleteLogLevel(String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        owner.setLogLevel((String) null);
        ownerCurator.merge(owner);
    }

    @Override
    @Transactional
    public Stream<ConsumerDTOArrayElement> listConsumers(
        @Verify(value = Owner.class, subResource = SubResource.CONSUMERS) String ownerKey,
        String username,
        Set<String> typeLabels,
        @Verify(value = Consumer.class, nullable = true) List<String> uuids,
        List<String> hypervisorIds,
        List<String> facts,
        Integer page, Integer perPage, String order, String sortBy) {
        Owner owner = findOwnerByKey(ownerKey);
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
            int maxSize = config.getInt(ConfigProperties.PAGING_MAX_PAGE_SIZE);
            if (count > maxSize) {
                String errmsg = this.i18n.tr("This endpoint does not support returning more than {0} " +
                    "results at a time, please use paging.", maxSize);
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
    @Transactional
    public Integer countConsumers(
        @Verify(value = Owner.class, subResource = SubResource.CONSUMERS) String ownerKey,
        String username,
        Set<String> typeLabels,
        @Verify(value = Consumer.class, nullable = true) List<String> uuids,
        List<String> hypervisorIds) {

        Owner owner = this.findOwnerByKey(ownerKey);
        List<ConsumerType> types = this.consumerTypeValidator.findAndValidateTypeLabels(typeLabels);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setOwner(owner)
            .setUsername(username)
            .setUuids(uuids)
            .setTypes(types)
            .setHypervisorIds(hypervisorIds);

        return (int) this.consumerCurator.getConsumerCount(queryArgs);
    }

    @Override
    @Transactional
    public Stream<PoolDTO> listOwnerPools(
        @Verify(value = Owner.class, subResource = SubResource.POOLS) String ownerKey,
        String consumerUuid, String activationKeyName,
        String productId, String subscriptionId, Boolean listAll, OffsetDateTime activeOn,
        List<String> matches, List<String> attrFilters, Boolean addFuture, Boolean onlyFuture,
        OffsetDateTime after, List<String> poolIds, Integer page, Integer perPage,
        String sortBy, String order) {

        Principal principal = this.principalProvider.get();
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);

        Owner owner = findOwnerByKey(ownerKey);

        // set default to current date
        Date activeOnDate = activeOn != null ? Util.toDate(activeOn) : new Date();
        Date afterDate = Util.toDate(after);

        Consumer c = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("Unit: {0} not found",
                    consumerUuid));
            }

            if (!c.getOwnerId().equals(owner.getId())) {
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
            key = activationKeyCurator.getByKeyName(owner, activationKeyName);
            if (key == null) {
                throw new BadRequestException(
                    i18n.tr("ActivationKey with id {0} could not be found.", activationKeyName));
            }
        }

        if (addFuture && onlyFuture) {
            throw new BadRequestException(
                i18n.tr("The flags add_future and only_future cannot be used at the same time."));
        }

        if (afterDate != null && (addFuture || onlyFuture)) {
            throw new BadRequestException(
                i18n.tr("The flags add_future and only_future cannot be used with the parameter after."));
        }

        // Process the filters passed for the attributes
        PoolQualifier qualifier = new PoolQualifier();

        new KeyValueStringParser(this.i18n).parseKeyValuePairs(attrFilters)
            .forEach(kvpair -> qualifier.addAttribute(kvpair.getKey(), kvpair.getValue()));

        if (poolIds != null && !poolIds.isEmpty()) {
            qualifier.addIds(poolIds);
        }

        if (matches != null) {
            matches.stream()
                .filter(elem -> elem != null && !elem.isEmpty())
                .forEach(qualifier::addMatch);
        }

        qualifier.setConsumer(c)
            .addIds(poolIds)
            .addProductId(productId)
            .addSubscriptionId(subscriptionId)
            .setOwnerId(owner.getId())
            .setActiveOn(afterDate == null ? activeOnDate : null)
            .setAddFuture(addFuture)
            .setOnlyFuture(onlyFuture)
            .setAfter(afterDate)
            .setIncludeWarnings(listAll);

        if (pageRequest != null) {
            qualifier.setOffset(pageRequest.getPage())
                .setLimit(pageRequest.getPerPage());

            boolean reverse = pageRequest.getOrder() == PageRequest.DEFAULT_ORDER;
            qualifier.addOrder(pageRequest.getSortBy(), reverse);
        }

        if (key != null) {
            qualifier.setActivationKey(key);
        }

        Page<List<Pool>> poolPage = null;
        try {
            poolPage = poolManager.listAvailableEntitlementPools(qualifier)
                .setPageRequest(pageRequest);
        }
        catch (InvalidOrderKeyException e) {
            throw new BadRequestException(e.getMessage(), e);
        }

        if (qualifier.getOffset() != null && qualifier.getLimit() != null) {
            List<Pool> resultingPools = poolPage.getPageData();
            resultingPools = poolCurator.takeSubList(qualifier, resultingPools);
            poolPage.setPageData(resultingPools);
        }

        List<Pool> poolList = poolPage.getPageData();
        calculatedAttributesUtil.setCalculatedAttributes(poolList, activeOnDate);
        calculatedAttributesUtil.setQuantityAttributes(poolList, c, activeOnDate);

        // Store the page for the LinkHeaderResponseFilter
        ResteasyContext.pushContext(Page.class, poolPage);

        return poolList.stream()
            .map(this.translator.getStreamMapper(Pool.class, PoolDTO.class));
    }

    @Override
    @Transactional
    public List<SubscriptionDTO> getOwnerSubscriptions(String ownerKey) {
        Owner owner = this.findOwnerByKey(ownerKey);
        List<SubscriptionDTO> subscriptions = new LinkedList<>();

        for (Pool pool : this.poolService.listPoolsByOwner(owner)) {

            SourceSubscription srcsub = pool.getSourceSubscription();
            if (srcsub != null && PRIMARY_POOL_SUB_KEY.equalsIgnoreCase(srcsub.getSubscriptionSubKey())) {
                subscriptions.add(this.translator.translate(pool, SubscriptionDTO.class));
            }
        }

        return subscriptions;
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO refreshPools(String ownerKey, Boolean autoCreateOwner) {
        Owner owner = ownerCurator.getByKey(ownerKey);
        if (owner == null) {
            if (autoCreateOwner && ownerService.isOwnerKeyValidForCreation(ownerKey)) {
                owner = createOwnerFromDTO(new OwnerDTO().key(ownerKey).displayName(ownerKey));
            }
            else {
                throw new NotFoundException(i18n.tr("owner with key: {0} was not found.", ownerKey));
            }
        }

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        JobConfig config = RefreshPoolsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(true);

        return queueJob(config);
    }

    @Override
    @Transactional
    public PoolDTO createPool(@Verify(Owner.class) String ownerKey, PoolDTO inputPoolDTO) {
        log.info("Creating custom pool for owner {}: {}", ownerKey, inputPoolDTO);

        this.validator.validateCollectionElementsNotNull(
            inputPoolDTO::getDerivedProvidedProducts, inputPoolDTO::getProvidedProducts);

        Pool pool = new Pool();

        // Correct owner
        Owner owner = findOwnerByKey(ownerKey);
        pool.setOwner(owner);

        // Populate the rest of the pool
        this.populateEntity(pool, inputPoolDTO);

        pool.setContractNumber(inputPoolDTO.getContractNumber());
        pool.setOrderNumber(inputPoolDTO.getOrderNumber());
        pool.setAccountNumber(inputPoolDTO.getAccountNumber());
        pool.setUpstreamPoolId(inputPoolDTO.getUpstreamPoolId());
        pool.setSubscriptionSubKey(inputPoolDTO.getSubscriptionSubKey());
        pool.setSubscriptionId(inputPoolDTO.getSubscriptionId());

        if (inputPoolDTO.getProductId() == null || inputPoolDTO.getProductId().isEmpty()) {
            throw new BadRequestException(i18n.tr("Pool product ID not specified"));
        }

        // API-created pools can only create pools referencing products from the org's namespace
        Product product = this.resolveProductId(pool.getOwner(), inputPoolDTO.getProductId());

        if (!owner.getKey().equals(product.getNamespace())) {
            throw new ForbiddenException(this.i18n.tr(
                "Cannot create pools using products defined outside of the organization's namespace"));
        }

        // Product is cleared, use it.
        pool.setProduct(product);

        if (inputPoolDTO.getSourceEntitlement() != null) {
            pool.setSourceEntitlement(findEntitlement(inputPoolDTO.getSourceEntitlement().getId()));
        }

        pool = poolManager.createAndEnrichPools(pool);

        log.debug("Synchronizing last content update for org: {}", owner);
        owner.syncLastContentUpdate();
        this.ownerCurator.merge(owner);

        return this.translator.translate(pool, PoolDTO.class);
    }

    @Override
    @Transactional
    public void updatePool(@Verify(Owner.class) String ownerKey,
        PoolDTO newPoolDTO) {

        this.validator.validateCollectionElementsNotNull(
            newPoolDTO::getDerivedProvidedProducts, newPoolDTO::getProvidedProducts);

        Pool currentPool = this.poolService.get(newPoolDTO.getId());
        if (currentPool == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find a pool with the ID \"{0}\"", newPoolDTO.getId()));
        }

        // Verify the existing pool belongs to the specified owner
        // If the pool isn't valid for this owner, we'll pretend it doesn't exist to avoid any
        // potential security concerns.
        if (currentPool.getOwner() == null || !ownerKey.equals(currentPool.getOwner().getKey())) {
            throw new NotFoundException(
                i18n.tr("Pool \"{0}\" does not belong to the specified owner \"{1}\"",
                    currentPool.getId(), ownerKey));
        }

        // Verify the pool type is one that allows modifications
        if (currentPool.getType() != PoolType.NORMAL) {
            throw new BadRequestException(
                i18n.tr("Cannot update bonus pools, as they are auto generated"));
        }

        Pool newPool = new Pool();
        this.populateEntity(newPool, newPoolDTO);

        // Owner & id have already been validated/resolved.
        newPool.setOwner(currentPool.getOwner());
        newPool.setId(newPoolDTO.getId());

        /* These are @JsonIgnored. If a client creates a pool and subsequently
         * wants to update it , we need to ensure Products are set appropriately.
         */
        newPool.setProduct(currentPool.getProduct());

        // Forcefully set fields we don't allow the client to change.
        newPool.setSourceSubscription(currentPool.getSourceSubscription());
        newPool.setContractNumber(currentPool.getContractNumber());
        newPool.setAccountNumber(currentPool.getAccountNumber());
        newPool.setOrderNumber(currentPool.getOrderNumber());
        newPool.setUpstreamPoolId(currentPool.getUpstreamPoolId());
        newPool.setUpstreamEntitlementId(currentPool.getUpstreamEntitlementId());
        newPool.setUpstreamConsumerId(currentPool.getUpstreamConsumerId());
        newPool.setCertificate(currentPool.getCertificate());
        newPool.setCdn(currentPool.getCdn());

        // Update fields the client has changed, or otherwise set the current value.
        if (newPoolDTO.getStartDate() == null) {
            newPool.setStartDate(currentPool.getStartDate());
        }

        if (newPoolDTO.getEndDate() == null) {
            newPool.setEndDate(currentPool.getEndDate());
        }

        if (newPoolDTO.getQuantity() == null) {
            newPool.setQuantity(currentPool.getQuantity());
        }

        if (newPoolDTO.getAttributes() == null) {
            newPool.setAttributes(currentPool.getAttributes());
        }

        // Apply changes to the pool and its derived pools
        this.poolManager.updatePrimaryPool(newPool);

        Owner owner = newPool.getOwner();
        log.debug("Synchronizing last content update for org: {}", owner);
        owner.syncLastContentUpdate();
        this.ownerCurator.merge(owner);
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO undoImports(@Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);

        if (this.exportCurator.getByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner) == null) {
            throw new NotFoundException("No import found for owner " + ownerKey);
        }

        JobConfig config = UndoImportsJob.createJobConfig()
            .setOwner(owner);

        return queueJob(config);
    }

    private ConflictOverrides processConflictOverrideParams(List<String> overrideConflicts) {
        if (overrideConflicts != null && overrideConflicts.size() == 1) {
            // Impl note:
            // To maintain backward compatibility, we need to process an override of "true" as
            // overriding the MANIFEST_OLD conflict, and "false" as overriding nothing.
            String override = overrideConflicts.get(0);

            if ("true".equalsIgnoreCase(override)) {
                overrideConflicts = List.of("MANIFEST_OLD");
            }
            else if ("false".equalsIgnoreCase(override)) {
                overrideConflicts = List.of();
            }
        }

        try {
            ConflictOverrides overrides = new ConflictOverrides(overrideConflicts);

            if (log.isDebugEnabled()) {
                for (String override : overrides.asStringArray()) {
                    log.debug("Overriding conflict if encountered: {}", override);
                }
            }

            return overrides;
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException(i18n.tr("Unknown conflict to force"), e);
        }
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO importManifestAsync(
        @Verify(Owner.class) String ownerKey, List<String> force, MultipartInput input) {

        ConflictOverrides overrides = this.processConflictOverrideParams(force);
        Owner owner = this.findOwnerByKey(ownerKey);

        String archiveFilename = "";

        try {
            AttachedFile attached = AttachedFile.getAttachedFile(input);
            File archive = attached.getFile();
            archiveFilename = attached.getFilename("");

            log.info("Running async import of archive {} for owner {}", archive.getAbsolutePath(), owner);

            JobConfig config = this.manifestManager
                .importManifestAsync(owner, archive, archiveFilename, overrides);

            return queueJob(config);
        }
        catch (IOException e) {
            manifestManager.recordImportFailure(owner, e, archiveFilename);
            throw new IseException(i18n.tr("Error reading export archive"), e);
        }
        catch (ManifestFileServiceException e) {
            manifestManager.recordImportFailure(owner, e, archiveFilename);
            throw new IseException(i18n.tr("Error storing uploaded archive for asynchronous processing"), e);
        }
        catch (CandlepinException e) {
            manifestManager.recordImportFailure(owner, e, archiveFilename);
            throw e;
        }
    }

    @Override
    @Transactional
    public Stream<ImportRecordDTO> getImports(
        @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);

        return this.importRecordCurator.findRecords(owner)
            .stream()
            .map(this.translator.getStreamMapper(ImportRecord.class, ImportRecordDTO.class));
    }

    @Override
    @Transactional
    public SystemPurposeAttributesDTO getSyspurpose(@Verify(value = Owner.class,
        subResource = SubResource.POOLS, require = Access.READ_ONLY) String ownerKey) {

        Owner owner = findOwnerByKey(ownerKey);
        Map<String, Set<String>> attributeMap = this.poolCurator.getSyspurposeAttributesByOwner(owner);

        SystemPurposeAttributesDTO dto = new SystemPurposeAttributesDTO();
        dto.setOwner(translator.translate(owner, NestedOwnerDTO.class));
        dto.setSystemPurposeAttributes(attributeMap);
        return dto;
    }

    @Override
    @Transactional
    public SystemPurposeAttributesDTO getConsumersSyspurpose(
        @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        SystemPurposeAttributesDTO dto = new SystemPurposeAttributesDTO();
        List<String> consumerRoles = this.consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.ROLES);
        List<String> consumerUsages = this.consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.USAGE);
        List<String> consumerSLAs = this.consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.SERVICE_LEVEL);
        List<String> consumerServiceTypes = this.consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.SERVICE_TYPE);
        List<String> consumerAddons = this.consumerCurator.getDistinctSyspurposeAddonsByOwner(owner);

        Map<String, Set<String>> dtoMap = new HashMap<>();
        Arrays.stream(SystemPurposeAttributeType.values())
            .forEach(x -> dtoMap.put(x.toString(), new LinkedHashSet<>()));

        dtoMap.get(SystemPurposeAttributeType.ROLES.toString()).addAll(consumerRoles);
        dtoMap.get(SystemPurposeAttributeType.USAGE.toString()).addAll(consumerUsages);
        dtoMap.get(SystemPurposeAttributeType.SERVICE_LEVEL.toString()).addAll(consumerSLAs);
        dtoMap.get(SystemPurposeAttributeType.ADDONS.toString()).addAll(consumerAddons);
        dtoMap.get(SystemPurposeAttributeType.SERVICE_TYPE.toString()).addAll(consumerServiceTypes);

        dto.setOwner(translator.translate(owner, NestedOwnerDTO.class));
        dto.setSystemPurposeAttributes(dtoMap);
        return dto;
    }

    @Override
    @Transactional
    public UeberCertificateDTO createUeberCertificate(@Verify(Owner.class) String ownerKey) {
        Principal principal = this.principalProvider.get();
        UeberCertificate ueberCert = ueberCertGenerator.generate(ownerKey, principal.getUsername());

        return this.translator.translate(ueberCert, UeberCertificateDTO.class);
    }

    @Override
    @Transactional
    public UeberCertificateDTO getUeberCertificate(@Verify(Owner.class) String ownerKey) {
        Owner owner = this.findOwnerByKey(ownerKey);
        UeberCertificate ueberCert = ueberCertCurator.findForOwner(owner);
        if (ueberCert == null) {
            throw new NotFoundException(i18n.tr(
                "uber certificate for owner {0} was not found. Please generate one.", owner.getKey()));
        }

        return this.translator.translate(ueberCert, UeberCertificateDTO.class);
    }

    @Override
    @Transactional
    public List<UpstreamConsumerDTOArrayElement> getUpstreamConsumers(@Verify(Owner.class) String ownerKey) {
        Owner owner = this.findOwnerByKey(ownerKey);
        UpstreamConsumer consumer = owner.getUpstreamConsumer();
        UpstreamConsumerDTOArrayElement dto = this.translator.translate(consumer,
            UpstreamConsumerDTOArrayElement.class);

        // returning as a list for future proofing. today we support one, but
        // users of this api want to protect against having to change their code
        // when multiples are supported.
        return Arrays.asList(dto);
    }

    @Override
    @Transactional
    public Stream<ConsumerDTOArrayElement> getHypervisors(@Verify(Owner.class) String ownerKey,
        List<String> hypervisorIds, Integer page, Integer perPage, String order, String sortBy) {

        Owner owner = ownerCurator.getByKey(ownerKey);
        List<Consumer> hypervisors;

        if (hypervisorIds == null || hypervisorIds.isEmpty()) {
            hypervisors = listHypervisorsByOwner(owner);
        }
        else {
            hypervisors = listHypervisorsByIds(owner, hypervisorIds);
        }

        return hypervisors.stream()
            .map(this.translator.getStreamMapper(Consumer.class, ConsumerDTOArrayElement.class));
    }

    private List<Consumer> listHypervisorsByOwner(Owner owner) {
        int ownerHypervisorCount = this.consumerCurator.countHypervisorsForOwner(owner.getId());
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        validateRequestSize(pageRequest, ownerHypervisorCount);

        if (pageRequest != null) {
            Page<Stream<ConsumerDTOArrayElement>> page = new Page<>();
            page.setPageRequest(pageRequest);

            page.setMaxRecords(ownerHypervisorCount);

            // Store the page for the LinkHeaderResponseFilter
            ResteasyContext.pushContext(Page.class, page);
        }

        return this.consumerCurator.getHypervisorsForOwner(owner.getId(), pageRequest);
    }

    private List<Consumer> listHypervisorsByIds(Owner owner, List<String> hypervisorIds) {
        int ownerHypervisorCount = this.consumerCurator.countHypervisorsBulk(owner.getId(), hypervisorIds);
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        validateRequestSize(pageRequest, ownerHypervisorCount);

        if (pageRequest != null) {
            Page<Stream<ConsumerDTOArrayElement>> page = new Page<>();
            page.setPageRequest(pageRequest);

            page.setMaxRecords(ownerHypervisorCount);

            // Store the page for the LinkHeaderResponseFilter
            ResteasyContext.pushContext(Page.class, page);
        }

        return this.consumerCurator.getHypervisorsBulk(owner.getId(), hypervisorIds, pageRequest);
    }

    private void validateRequestSize(PageRequest pageRequest, int ownerHypervisorCount) {
        if (pageRequest == null && ownerHypervisorCount > this.maxPagingSize) {
            String errmsg = this.i18n.tr("This endpoint does not support returning more than {0} " +
                "results at a time, please use paging.", this.maxPagingSize);
            throw new BadRequestException(errmsg);
        }
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO claim(String anonymousOwnerKey, ClaimantOwner claimantOwner) {
        if (claimantOwner == null || StringUtils.isBlank(claimantOwner.getClaimantOwnerKey())) {
            throw new BadRequestException(this.i18n.tr("Claimant owner has to be present!"));
        }

        Owner originOwner = findOwnerByKey(anonymousOwnerKey);
        if (Util.isFalse(originOwner.getAnonymous())) {
            throw new BadRequestException(this.i18n.tr("Origin owner has to be anonymous!"));
        }

        Owner destinationOwner = this.ownerCurator.getByKey(claimantOwner.getClaimantOwnerKey());
        if (destinationOwner == null) {
            // If the owner does not exist in CP, check if they exist upstream.
            // If they do, create a copy in CP
            if (ownerService.isOwnerKeyValidForCreation(claimantOwner.getClaimantOwnerKey())) {
                Owner ownerToCreate = new Owner()
                    .setKey(claimantOwner.getClaimantOwnerKey())
                    .setDisplayName(claimantOwner.getClaimantOwnerKey())
                    .setContentAccessModeList(ContentAccessManager.defaultContentAccessModeList())
                    .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

                this.ownerCurator.create(ownerToCreate);
            }
            else {
                throw new BadRequestException(
                    this.i18n.tr("Claimant owner does not exist in Candlepin or upstream!"));
            }
        }
        else {
            if (Boolean.TRUE.equals(destinationOwner.getAnonymous())) {
                throw new BadRequestException(this.i18n.tr("Claimant owner cannot be anonymous!"));
            }
        }

        boolean claimantOwnerMatches = StringUtils.equals(
            claimantOwner.getClaimantOwnerKey(), originOwner.getClaimantOwner());
        if (Boolean.TRUE.equals(originOwner.getClaimed()) && !claimantOwnerMatches) {
            throw new BadRequestException(this.i18n.tr("Claimant owners don't match!"));
        }

        if (Util.isFalse(originOwner.getClaimed()) && originOwner.getClaimantOwner() == null) {
            originOwner.setClaimed(true);
            originOwner.setClaimantOwner(claimantOwner.getClaimantOwnerKey());
        }

        JobConfig jobConfig = ConsumerMigrationJob.createConfig()
            .setOriginOwner(anonymousOwnerKey)
            .setDestinationOwner(claimantOwner.getClaimantOwnerKey());

        return queueJob(jobConfig);
    }

    private AsyncJobStatusDTO queueJob(JobConfig jobConfig) {
        try {
            AsyncJobStatus job = this.jobManager.queueJob(jobConfig);
            return this.translator.translate(job, AsyncJobStatusDTO.class);
        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                jobConfig.getJobKey());
            throw new IseException(errmsg, e);
        }
    }

    private Owner createOwnerFromDTO(OwnerDTO ownerDTO) {
        // Verify that we have an owner key (as required)
        if (StringUtils.isBlank(ownerDTO.getKey())) {
            throw new BadRequestException(i18n.tr("Owners must be created with a valid key"));
        }

        Owner owner = new Owner();
        owner.setKey(ownerDTO.getKey());

        // Check that the default service level is *not* set at this point
        if (!StringUtils.isBlank(ownerDTO.getDefaultServiceLevel())) {
            throw new BadRequestException(
                i18n.tr("The default service level cannot be specified during owner creation"));
        }

        // Validate and set content access mode list & content access mode
        boolean configureContentAccess = false;

        final String defaultContentAccess = ContentAccessMode.getDefault().toDatabaseValue();
        final String defaultContentAccessList = ContentAccessManager.defaultContentAccessModeList();
        String contentAccessModeList = ownerDTO.getContentAccessModeList();
        String contentAccessMode = ownerDTO.getContentAccessMode();

        if (!StringUtils.isBlank(contentAccessMode) && !contentAccessMode.equals(defaultContentAccess)) {
            configureContentAccess = true;
        }
        else {
            contentAccessMode = defaultContentAccess;
        }

        if (!StringUtils.isBlank(contentAccessModeList) &&
            !contentAccessModeList.equals(defaultContentAccessList)) {
            configureContentAccess = true;
        }
        else {
            contentAccessModeList = defaultContentAccessList;
        }

        this.validateContentAccessModeChanges(owner, contentAccessModeList, contentAccessMode);

        // Translate the DTO to an entity Owner.
        this.populateEntity(owner, ownerDTO);
        owner.setContentAccessModeList(contentAccessModeList);
        owner.setContentAccessMode(contentAccessMode);
        owner.setAnonymous(ownerDTO.getAnonymous());
        owner.setClaimed(owner.getClaimed());

        // Try to persist the owner
        try {
            owner = this.ownerCurator.create(owner);

            if (owner == null) {
                throw new BadRequestException(i18n.tr("Could not create the Owner: {0}", owner));
            }

            if (configureContentAccess) {
                // Apply content access configuration if the user has given us non-default
                // content access settings
                owner = this.contentAccessManager
                    .updateOwnerContentAccess(owner, contentAccessModeList, contentAccessMode);
            }
        }
        catch (Exception e) {
            log.debug("Unable to create owner: ", e);
            throw new BadRequestException(i18n.tr("Could not create the Owner: {0}", owner), e);
        }

        log.info("Created owner: {}", owner);
        sink.emitOwnerCreated(owner);
        return owner;
    }

    // PUT /owners/{owner_key}/consumers/environments

    @Override
    @Transactional
    public void setConsumersToEnvironments(@Verify(Owner.class) String ownerKey,
        SetConsumerEnvironmentsDTO request) {

        List<String> consumerUuids = request.getConsumerUuids();
        if (consumerUuids == null || consumerUuids.isEmpty()) {
            throw new BadRequestException(i18n.tr("No consumer UUIDs provided"));
        }

        List<String> environmentIds = request.getEnvironmentIds();
        if (environmentIds == null) {
            throw new BadRequestException(i18n.tr("The provided environment ID list cannot be null"));
        }

        int consumerLimit = config.getInt(ConfigProperties.BULK_SET_CONSUMER_ENV_MAX_CONSUMER_LIMIT);
        if (consumerUuids.size() > consumerLimit) {
            throw new BadRequestException(i18n
                .tr("Number of consumer UUIDs exceeds the max size of {0}", consumerLimit));
        }

        int envLimit = config.getInt(ConfigProperties.BULK_SET_CONSUMER_ENV_MAX_ENV_LIMIT);
        if (environmentIds.size() > envLimit) {
            throw new BadRequestException(i18n
                .tr("Number of environment IDs exceeds the max size of {0}", envLimit));
        }

        Owner owner = findOwnerByKey(ownerKey);
        if (!ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue().equals(owner.getContentAccessMode())) {
            throw new BadRequestException(i18n.tr("Owner is not in SCA content access mode"));
        }

        Set<String> unknownConsumerUuids = consumerCurator
            .getNonExistentConsumerUuids(owner.getKey(), consumerUuids);
        if (!unknownConsumerUuids.isEmpty()) {
            throw new BadRequestException(i18n
                .tr("Unknown consumer UUIDs: {0}", unknownConsumerUuids));
        }

        NonNullLinkedHashSet<String> envSet = new NonNullLinkedHashSet<>();
        if (!environmentIds.isEmpty()) {
            try {
                envSet.addAll(environmentIds);
            }
            catch (IllegalArgumentException e) {
                throw new BadRequestException(this.i18n.tr("Environment IDs cannot be null"));
            }

            if (envSet.size() != environmentIds.size()) {
                throw new BadRequestException(i18n.tr("Request contains duplicate environment IDs"));
            }

            Set<String> unknownEnvIds = envCurator.getNonExistentEnvironmentIds(owner, environmentIds);
            if (!unknownEnvIds.isEmpty()) {
                throw new BadRequestException(i18n.tr("Unknown environment IDs: {0}", unknownEnvIds));
            }
        }

        consumerManager.setConsumersEnvironments(owner, consumerUuids, envSet);
    }

}

