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

import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
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
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerContentAccess;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ActivationKeyDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
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
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
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
import org.candlepin.model.CandlepinQuery;
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
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.InvalidOrderKeyException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.OwnerNotFoundException;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.SystemPurposeAttributeType;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
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
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.Util;

import ch.qos.logback.classic.Level;

import com.google.inject.persist.Transactional;

import org.apache.commons.collections.CollectionUtils;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.persistence.PersistenceException;



/**
 * Owner Resource
 */
public class OwnerResource implements OwnerApi {

    private static Logger log = LoggerFactory.getLogger(OwnerResource.class);

    private static final Pattern AK_CHAR_FILTER = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /** The maximum number of consumers to return per list or find request */
    private static final int MAX_CONSUMERS_PER_REQUEST = 1000;

    private OwnerCurator ownerCurator;
    private OwnerInfoCurator ownerInfoCurator;
    private ActivationKeyCurator activationKeyCurator;
    private OwnerServiceAdapter ownerService;
    private ConsumerCurator consumerCurator;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private ManifestManager manifestManager;
    private ExporterMetadataCurator exportCurator;
    private ImportRecordCurator importRecordCurator;
    private ContentAccessManager contentAccessManager;
    private PoolManager poolManager;
    private OwnerManager ownerManager;
    private EntitlementCurator entitlementCurator;
    private UeberCertificateCurator ueberCertCurator;
    private UeberCertificateGenerator ueberCertGenerator;
    private EnvironmentCurator envCurator;
    private CalculatedAttributesUtil calculatedAttributesUtil;
    private ContentOverrideValidator contentOverrideValidator;
    private ServiceLevelValidator serviceLevelValidator;
    private Configuration config;
    private ConsumerTypeValidator consumerTypeValidator;
    private OwnerProductCurator ownerProductCurator;
    private ModelTranslator translator;
    private JobManager jobManager;
    private DTOValidator validator;
    private PrincipalProvider principalProvider;

    @Inject
    @SuppressWarnings("checkstyle:parameternumber")
    public OwnerResource(OwnerCurator ownerCurator,
        ActivationKeyCurator activationKeyCurator,
        ConsumerCurator consumerCurator,
        I18n i18n,
        EventSink sink,
        EventFactory eventFactory,
        ContentAccessManager contentAccessManager,
        ManifestManager manifestManager,
        PoolManager poolManager,
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
        OwnerProductCurator ownerProductCurator,
        ModelTranslator translator,
        JobManager jobManager,
        DTOValidator validator,
        PrincipalProvider principalProvider) {

        this.ownerCurator = ownerCurator;
        this.ownerInfoCurator = ownerInfoCurator;
        this.activationKeyCurator = activationKeyCurator;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.exportCurator = exportCurator;
        this.importRecordCurator = importRecordCurator;
        this.contentAccessManager = contentAccessManager;
        this.poolManager = poolManager;
        this.manifestManager = manifestManager;
        this.ownerManager = ownerManager;
        this.entitlementCurator = entitlementCurator;
        this.ueberCertCurator = ueberCertCurator;
        this.ueberCertGenerator = ueberCertGenerator;
        this.envCurator = envCurator;
        this.calculatedAttributesUtil = calculatedAttributesUtil;
        this.contentOverrideValidator = contentOverrideValidator;
        this.serviceLevelValidator = serviceLevelValidator;
        this.ownerService = ownerService;
        this.config = config;
        this.consumerTypeValidator = consumerTypeValidator;
        this.ownerProductCurator = ownerProductCurator;
        this.translator = translator;
        this.jobManager = jobManager;
        this.validator = validator;
        this.principalProvider = principalProvider;
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
            pool = poolManager.get(poolId);
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
     * Returns the product object that is identified by the given product id and owner object,
     * if it is found in the system. Otherwise, it throws a NotFoundException.
     *
     * @param owner
     *  The owner of the product we are searching for
     *
     * @param productId
     *  The ID of the product to lookup
     *
     * @throws IllegalArgumentException
     *  if owner is null, or if productId is null or empty
     *
     * @throws BadRequestException
     *  if a product with the specified ID cannot be found within the context of the given owner
     *
     * @return
     *  the product with the specified product ID and owner
     */
    private Product findProduct(Owner owner, String productId) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        Product product = this.ownerProductCurator.getProductById(owner, productId);

        if (product == null) {
            throw new BadRequestException(
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
    protected void populateEntity(Owner entity, OwnerDTO dto) {
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
    protected void populateEntity(ActivationKey entity, ActivationKeyDTO dto) {
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
                        entity.addContentOverride(
                            new ActivationKeyContentOverride(entity, overrideDTO.getContentLabel(),
                            overrideDTO.getName(), overrideDTO.getValue()));
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

            dto.getProducts().stream()
                .map(keyprod -> this.findProduct(entity.getOwner(), keyprod.getProductId()).getId())
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
    protected void populateEntity(Environment entity, EnvironmentDTO dto) {

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
        entity.setDescription(dto.getDescription());
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
    protected void populateEntity(Pool entity, PoolDTO dto) {
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
    @Wrapped(element = "owners")
    public CandlepinQuery<OwnerDTO> listOwners(String keyFilter) {
        CandlepinQuery<Owner> query = keyFilter != null ?
            this.ownerCurator.getByKeys(Arrays.asList(keyFilter)) :
            this.ownerCurator.listAll();

        return this.translator.translateQuery(query, OwnerDTO.class);
    }

    @Override
    public OwnerDTO getOwner(@Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        return this.translator.translate(owner, OwnerDTO.class);
    }

    @Override
    public ContentAccessDTO getOwnerContentAccess(@Verify(Owner.class) String ownerKey) {
        try {
            OwnerContentAccess owner = this.ownerCurator.getOwnerContentAccess(ownerKey);
            String caMode = Util.firstOf(
                owner.getContentAccessMode(),
                ContentAccessManager.ContentAccessMode.getDefault().toDatabaseValue()
            );
            String caList = Util.firstOf(
                owner.getContentAccessModeList(),
                ContentAccessManager.getListDefaultDatabaseValue()
            );
            return new ContentAccessDTO()
                .contentAccessMode(caMode)
                .contentAccessModeList(Util.toList(caList));
        }
        catch (OwnerNotFoundException e) {
            throw new NotFoundException("Owner was not found!", e);
        }
    }

    @Override
    public OwnerInfo getOwnerInfo(
        @Verify(value = Owner.class, subResource = SubResource.CONSUMERS) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        return ownerInfoCurator.getByOwner(owner);
    }

    @Override
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

        // Do the bulk of our entity population
        this.populateEntity(owner, dto);

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
                calist = ContentAccessManager.getListDefaultDatabaseValue();
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
                calist = ContentAccessManager.getListDefaultDatabaseValue();
            }

            String[] modes = calist.split(",");
            if (!ArrayUtils.contains(modes, camode)) {
                throw new BadRequestException(this.i18n.tr("Content access mode is not present " +
                    "in the content access mode list for this org: {0}", camode));
            }
        }
    }

    @Override
    public void deleteOwner(String ownerKey,
        Boolean revoke, Boolean force) {

        Owner owner = findOwnerByKey(ownerKey);
        Event event = eventFactory.ownerDeleted(owner);

        try {
            ownerManager.cleanupAndDelete(owner, Boolean.valueOf(revoke));
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
    public AsyncJobStatusDTO healEntire(@Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(new Date());

        try {
            AsyncJobStatus job = this.jobManager.queueJob(config);
            return this.translator.translate(job, AsyncJobStatusDTO.class);
        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                config.getJobKey());
            log.error(errmsg, e);
            throw new IseException(errmsg, e);
        }
    }

    @Override
    public Set<String> ownerServiceLevels(
        @Verify(value = Owner.class, subResource = SubResource.SERVICE_LEVELS) String ownerKey,
        String exempt) {
        Owner owner = findOwnerByKey(ownerKey);
        Principal principal = this.principalProvider.get();

        if (principal.getType().equals("consumer")) {
            Consumer c = consumerCurator.findByUuid(principal.getName());
            if (c.isDev()) {
                Set<String> result = new HashSet<>();
                result.add("");
                return result;
            }
        }

        // test is on the string "true" and is case insensitive.
        return poolManager.retrieveServiceLevelsForOwner(owner.getId(), Boolean.parseBoolean(exempt));
    }

    @Override
    public CandlepinQuery<ActivationKeyDTO> ownerActivationKeys(
        @Verify(value = Owner.class, subResource = SubResource.ACTIVATION_KEYS) String ownerKey,
        String keyName) {

        Owner owner = findOwnerByKey(ownerKey);

        CandlepinQuery<ActivationKey> keys = this.activationKeyCurator.listByOwner(owner, keyName);
        return translator.translateQuery(keys, ActivationKeyDTO.class);
    }

    @Override
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

        serviceLevelValidator.validate(owner.getId(), dto.getServiceLevel());

        ActivationKey key = new ActivationKey();
        this.populateEntity(key, dto);
        key.setOwner(owner);

        ActivationKey newKey = activationKeyCurator.create(key);
        sink.emitActivationKeyCreated(newKey);

        return translator.translate(newKey, ActivationKeyDTO.class);
    }

    @Override
    public EnvironmentDTO createEnv(@Verify(Owner.class) String ownerKey, EnvironmentDTO envDTO) {
        Environment env = new Environment();
        NestedOwnerDTO ownerDTO = new NestedOwnerDTO().key(ownerKey);
        envDTO.setOwner(ownerDTO);
        populateEntity(env, envDTO);

        env = envCurator.create(env);
        return translator.translate(env, EnvironmentDTO.class);
    }

    @Override
    public CandlepinQuery<EnvironmentDTO> listEnvironments(
        @Verify(Owner.class) String ownerKey, String envName) {
        Owner owner = findOwnerByKey(ownerKey);
        CandlepinQuery<Environment> query = envName == null ?
            envCurator.listForOwner(owner) :
            envCurator.listForOwnerByName(owner, envName);
        return translator.translateQuery(query, EnvironmentDTO.class);
    }

    @Override
    public OwnerDTO setLogLevel(String ownerKey, String level) {
        Owner owner = findOwnerByKey(ownerKey);

        Level logLevel = Level.toLevel(level, null);
        if (logLevel == null) {
            throw new BadRequestException(i18n.tr("{0} is not a valid log level", level));
        }

        owner.setLogLevel(logLevel.toString());
        owner = ownerCurator.merge(owner);

        return this.translator.translate(owner, OwnerDTO.class);
    }

    @Override
    public void deleteLogLevel(String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);
        owner.setLogLevel((String) null);
        ownerCurator.merge(owner);
    }

    @Override
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

    @Transactional
    @Override
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
        PoolFilterBuilder poolFilters = new PoolFilterBuilder();

        new KeyValueStringParser(this.i18n).parseKeyValuePairs(attrFilters)
            .forEach(kvpair -> poolFilters.addAttributeFilter(kvpair.getKey(), kvpair.getValue()));

        if (matches != null) {
            matches.stream()
                .filter(elem -> elem != null && !elem.isEmpty())
                .forEach(elem -> poolFilters.addMatchesFilter(elem));
        }

        if (poolIds != null && !poolIds.isEmpty()) {
            poolFilters.addIdFilters(poolIds);
        }

        Page<List<Pool>> poolPage = poolManager.listAvailableEntitlementPools(
            c, key, owner.getId(), productId, subscriptionId, afterDate == null ? activeOnDate : null,
            listAll, poolFilters, pageRequest, addFuture, onlyFuture, afterDate);

        List<Pool> poolList = poolPage.getPageData();
        calculatedAttributesUtil.setCalculatedAttributes(poolList, activeOnDate);
        calculatedAttributesUtil.setQuantityAttributes(poolList, c, activeOnDate);

        // Store the page for the LinkHeaderResponseFilter
        ResteasyContext.pushContext(Page.class, poolPage);

        return poolList.stream()
            .map(this.translator.getStreamMapper(Pool.class, PoolDTO.class));
    }

    @Override
    public List<SubscriptionDTO> getOwnerSubscriptions(String ownerKey) {
        Owner owner = this.findOwnerByKey(ownerKey);
        List<SubscriptionDTO> subscriptions = new LinkedList<>();

        for (Pool pool : this.poolManager.listPoolsByOwner(owner).list()) {

            SourceSubscription srcsub = pool.getSourceSubscription();
            if (srcsub != null && PRIMARY_POOL_SUB_KEY.equalsIgnoreCase(srcsub.getSubscriptionSubKey())) {
                subscriptions.add(this.translator.translate(pool, SubscriptionDTO.class));
            }
        }

        return subscriptions;
    }

    @Override
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

        try {
            AsyncJobStatus job = this.jobManager.queueJob(config);
            return this.translator.translate(job, AsyncJobStatusDTO.class);
        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                config.getJobKey());
            log.error(errmsg, e);
            throw new IseException(errmsg, e);
        }
    }

    @Override
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

        pool.setProduct(findProduct(pool.getOwner(), inputPoolDTO.getProductId()));

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
    public void updatePool(@Verify(Owner.class) String ownerKey,
        PoolDTO newPoolDTO) {

        this.validator.validateCollectionElementsNotNull(
            newPoolDTO::getDerivedProvidedProducts, newPoolDTO::getProvidedProducts);

        Pool currentPool = this.poolManager.get(newPoolDTO.getId());
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
    public AsyncJobStatusDTO undoImports(@Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);

        if (this.exportCurator.getByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner) == null) {
            throw new NotFoundException("No import found for owner " + ownerKey);
        }

        JobConfig config = UndoImportsJob.createJobConfig()
            .setOwner(owner);

        try {
            AsyncJobStatus job = this.jobManager.queueJob(config);
            return this.translator.translate(job, AsyncJobStatusDTO.class);
        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                config.getJobKey());
            log.error(errmsg, e);
            throw new IseException(errmsg, e);
        }
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

            try {
                AsyncJobStatus job = this.jobManager.queueJob(config);
                return this.translator.translate(job, AsyncJobStatusDTO.class);
            }
            catch (JobException e) {
                String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                    config.getJobKey());

                log.error(errmsg, e);
                throw new IseException(errmsg, e);
            }
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
    public Iterable<ImportRecordDTO> getImports(
        @Verify(Owner.class) String ownerKey) {
        Owner owner = findOwnerByKey(ownerKey);

        return this.translator.translateQuery(this.importRecordCurator.findRecords(owner),
            ImportRecordDTO.class);
    }

    @Override
    public SystemPurposeAttributesDTO getSyspurpose(
        @Verify(value = Owner.class, subResource = SubResource.POOLS, require = Access.READ_ONLY)
        String ownerKey) {

        Owner owner = findOwnerByKey(ownerKey);
        Map<String, Set<String>> attributeMap = ownerProductCurator.getSyspurposeAttributesByOwner(owner);

        SystemPurposeAttributesDTO dto = new SystemPurposeAttributesDTO();
        dto.setOwner(translator.translate(owner, NestedOwnerDTO.class));
        dto.setSystemPurposeAttributes(attributeMap);
        return dto;
    }

    @Override
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
    public UeberCertificateDTO createUeberCertificate(
        @Verify(Owner.class) String ownerKey) {
        Principal principal = this.principalProvider.get();
        UeberCertificate ueberCert = ueberCertGenerator.generate(ownerKey, principal);

        return this.translator.translate(ueberCert, UeberCertificateDTO.class);
    }

    @Override
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
    public List<UpstreamConsumerDTOArrayElement> getUpstreamConsumers(@Verify(Owner.class) String ownerKey) {

        Owner owner = this.findOwnerByKey(ownerKey);
        UpstreamConsumer consumer = owner.getUpstreamConsumer();
        UpstreamConsumerDTOArrayElement dto =
            this.translator.translate(consumer, UpstreamConsumerDTOArrayElement.class);

        // returning as a list for future proofing. today we support one, but
        // users of this api want to protect against having to change their code
        // when multiples are supported.
        return Arrays.asList(dto);
    }

    @Override
    public CandlepinQuery<ConsumerDTOArrayElement> getHypervisors(
        @Verify(Owner.class) String ownerKey, List<String> hypervisorIds) {

        Owner owner = ownerCurator.getByKey(ownerKey);
        CandlepinQuery<Consumer> query = (hypervisorIds == null || hypervisorIds.isEmpty()) ?
            this.consumerCurator.getHypervisorsForOwner(owner.getId()) :
            this.consumerCurator.getHypervisorsBulk(hypervisorIds, owner.getId());
        return translator.translateQuery(query, ConsumerDTOArrayElement.class);
    }

    /**
     * Retrieves an Owner instance for the owner with the specified key/account. If a matching owner
     * could not be found, this method throws an exception.
     *
     * @param key
     *  The key for the owner to retrieve
     *
     * @throws NotFoundException
     *  if an owner could not be found for the specified key.
     *
     * @return
     *  the Owner instance for the owner with the specified key.
     *
     * @httpcode 200
     * @httpcode 404
     */
    protected Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.getByKey(key);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

    public boolean removeContent(ProductDTO product, Set<String> contentIds) {
        if (contentIds == null) {
            throw new IllegalArgumentException("contentId is null");
        }
        if (contentIds.isEmpty()) {
            return false;
        }
        int originalSize = product.getProductContent().size();
        Set<ProductContentDTO> updatedContents = product.getProductContent().stream()
            .filter(content -> !contentIds.contains(content.getContent().getId()))
            .collect(Collectors.toSet());

        product.setProductContent(updatedContents);

        return originalSize != updatedContents.size();
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
        final String defaultContentAccessList = ContentAccessManager.getListDefaultDatabaseValue();
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
}
