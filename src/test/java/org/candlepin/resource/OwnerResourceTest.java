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

import static org.candlepin.model.SourceSubscription.DERIVED_POOL_SUB_KEY;
import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.ImportJob;
import org.candlepin.audit.EventAdapter;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerContentAccess;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.ProductManager;
import org.candlepin.dto.api.server.v1.ActivationKeyDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.AttributeDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.server.v1.ContentAccessDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.dto.api.server.v1.EntitlementDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.PoolDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.api.server.v1.ReleaseVerDTO;
import org.candlepin.dto.api.server.v1.SystemPurposeAttributesDTO;
import org.candlepin.dto.api.server.v1.UeberCertificateDTO;
import org.candlepin.dto.api.server.v1.UpstreamConsumerDTOArrayElement;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerCurator.ConsumerQueryArguments;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.OwnerNotFoundException;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Role;
import org.candlepin.model.SystemPurposeAttributeType;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.ResolverUtil;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.impl.DefaultOwnerServiceAdapter;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.ImporterException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.Util;

import org.hibernate.exception.ConstraintViolationException;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedMap;


public class OwnerResourceTest extends DatabaseTestFixture {
    private static final String OWNER_NAME = "Jar Jar Binks";
    @Inject private CandlepinPoolManager poolManager;
    @Inject private I18n i18n;
    @Inject private OwnerResource ownerResource;
    @Inject private CalculatedAttributesUtil calculatedAttributesUtil;
    @Inject private DevConfig config;
    @Inject private ContentOverrideValidator contentOverrideValidator;
    @Inject private UeberCertificateGenerator ueberCertGenerator;
    @Inject private UeberCertificateCurator ueberCertCurator;
    @Inject private DTOValidator dtoValidator;
    @Inject protected ContentAccessManager contentAccessManager;
    @Inject protected ContentManager contentManager;
    @Inject protected ProductManager productManager;

    // Mocks used to build the owner resource
    private ActivationKeyCurator mockActivationKeyCurator;
    private ConsumerCurator mockConsumerCurator;
    private ConsumerTypeCurator mockConsumerTypeCurator;
    private EntitlementCurator mockEntitlementCurator;
    private EnvironmentCurator mockEnvironmentCurator;
    private ExporterMetadataCurator mockExportCurator;
    private ImportRecordCurator mockImportRecordCurator;
    private OwnerCurator mockOwnerCurator;
    private OwnerInfoCurator mockOwnerInfoCurator;
    private OwnerProductCurator mockOwnerProductCurator;
    private ProductCurator mockProductCurator;
    private PrincipalProvider principalProvider;
    private UeberCertificateCurator mockUeberCertCurator;
    private UeberCertificateGenerator mockUeberCertificateGenerator;

    private EventSink mockEventSink;
    private EventFactory mockEventFactory;
    private EventAdapter mockEventAdapter;

    private ManifestManager mockManifestManager;
    private OwnerManager mockOwnerManager;
    private PoolManager mockPoolManager;
    private JobManager mockJobManager;

    private ResolverUtil resolverUtil;
    private OwnerServiceAdapter ownerServiceAdapter;
    private ServiceLevelValidator serviceLevelValidator;
    private ConsumerTypeValidator consumerTypeValidator;

    private JobManager jobManager;
    private Owner owner;
    private List<Owner> owners;
    private Product product;
    private Set<String> typeLabels;
    private List<String> skus;
    private List<String> subscriptionIds;
    private List<String> contracts;

    @BeforeEach
    public void setUp() {
        this.jobManager = mock(JobManager.class);
        owner = ownerCurator.create(new Owner(OWNER_NAME));
        owners = new ArrayList<>();
        owners.add(owner);
        product = this.createProduct(owner);
        typeLabels = null;
        skus = null;
        subscriptionIds = null;
        contracts = null;

        // Setup mocks and other such things...
        this.mockActivationKeyCurator = mock(ActivationKeyCurator.class);
        this.mockConsumerCurator = mock(ConsumerCurator.class);
        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.mockEntitlementCurator = mock(EntitlementCurator.class);
        this.mockEnvironmentCurator = mock(EnvironmentCurator.class);
        this.mockExportCurator = mock(ExporterMetadataCurator.class);
        this.mockImportRecordCurator = mock(ImportRecordCurator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);
        this.mockOwnerInfoCurator = mock(OwnerInfoCurator.class);
        this.mockOwnerProductCurator = mock(OwnerProductCurator.class);
        this.mockProductCurator = mock(ProductCurator.class);
        this.mockUeberCertCurator = mock(UeberCertificateCurator.class);
        this.mockUeberCertificateGenerator = mock(UeberCertificateGenerator.class);

        this.mockEventSink = mock(EventSink.class);
        this.mockEventFactory = mock(EventFactory.class);
        this.mockEventAdapter = mock(EventAdapter.class);

        this.mockManifestManager = mock(ManifestManager.class);
        this.mockOwnerManager = mock(OwnerManager.class);
        this.mockPoolManager = mock(PoolManager.class);
        this.mockJobManager = mock(JobManager.class);
        this.principalProvider = mock(PrincipalProvider.class);

        this.resolverUtil = new ResolverUtil(this.i18n, this.mockOwnerCurator, this.mockOwnerProductCurator,
            this.mockProductCurator);
        this.ownerServiceAdapter = new DefaultOwnerServiceAdapter(this.mockOwnerCurator, this.i18n);
        this.serviceLevelValidator = new ServiceLevelValidator(this.i18n, this.mockPoolManager,
            this.mockOwnerCurator);
        this.consumerTypeValidator = new ConsumerTypeValidator(this.mockConsumerTypeCurator, this.i18n);
    }

    private OwnerResource buildOwnerResource() {
        return new OwnerResource(this.mockOwnerCurator, this.mockActivationKeyCurator,
            this.mockConsumerCurator, this.i18n, this.mockEventSink, this.mockEventFactory,
            this.contentAccessManager, this.mockManifestManager,
            this.mockPoolManager, this.mockOwnerManager, this.mockExportCurator, this.mockOwnerInfoCurator,
            this.mockImportRecordCurator, this.mockEntitlementCurator, this.mockUeberCertCurator,
            this.mockUeberCertificateGenerator, this.mockEnvironmentCurator, this.calculatedAttributesUtil,
            this.contentOverrideValidator, this.serviceLevelValidator, this.ownerServiceAdapter, this.config,
            this.consumerTypeValidator, this.mockOwnerProductCurator, this.modelTranslator,
            this.mockJobManager, this.dtoValidator, this.principalProvider);
    }

    private ProductDTO buildTestProductDTO() {
        return TestUtil.createProductDTO("test_product")
            .addAttributesItem(this.createAttribute(Product.Attributes.VERSION, "1.0"))
            .addAttributesItem(this.createAttribute(Product.Attributes.VARIANT, "server"))
            .addAttributesItem(this.createAttribute(Product.Attributes.TYPE, "SVC"))
            .addAttributesItem(this.createAttribute(Product.Attributes.ARCHITECTURE, "ALL"));
    }

    private AttributeDTO createAttribute(String name, String value) {
        return new AttributeDTO()
            .name(name)
            .value(value);
    }

    private void addContent(ProductDTO product, ContentDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new IllegalArgumentException("dto references incomplete content");
        }

        if (product.getProductContent() == null) {
            product.setProductContent(new HashSet<>());
        }

        ProductContentDTO content = new ProductContentDTO();
        content.setContent(dto);
        content.setEnabled(true);

        product.getProductContent().add(content);
    }

    @Test
    public void testCreateOwner() {
        assertNotNull(owner);
        assertNotNull(ownerCurator.get(owner.getId()));
        assertTrue(owner.getPools().isEmpty());
    }

    @Test
    public void testSimpleDeleteOwner() {
        String id = owner.getId();
        ownerResource.deleteOwner(owner.getKey(), true, true);
        owner = ownerCurator.get(id);
        assertNull(owner);
    }

    @Test
    public void testComplexDeleteOwner() throws Exception {
        // Create some consumers:
        Consumer c1 = createConsumer(owner);
        Consumer c2 = createConsumer(owner);

        // Create a pool for this owner:
        Pool pool = TestUtil.createPool(owner, product);
        poolCurator.create(pool);

        // Give those consumers entitlements:
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(pool.getId(), 1);
        poolManager.entitleByPools(c1, pQs);
        assertEquals(2, consumerCurator.listByOwner(owner).list().size());
        assertEquals(1, poolCurator.listByOwner(owner).list().size());
        assertEquals(1, entitlementCurator.listByOwner(owner).list().size());

        // Generate an ueber certificate for the Owner. This will need to
        // be cleaned up along with the owner deletion.
        UeberCertificate uCert = ueberCertGenerator.generate(owner.getKey(), setupAdminPrincipal("test"));
        assertNotNull(uCert);

        ownerResource.deleteOwner(owner.getKey(), true,  true);

        assertEquals(0, consumerCurator.listByOwner(owner).list().size());
        assertNull(consumerCurator.findByUuid(c1.getUuid()));
        assertNull(consumerCurator.findByUuid(c2.getUuid()));
        assertEquals(0, poolCurator.listByOwner(owner).list().size());
        assertEquals(0, entitlementCurator.listByOwner(owner).list().size());
        assertNull(ueberCertCurator.findForOwner(owner));
    }


    @Test
    public void testConsumerRoleCannotGetOwner() {
        Consumer c = createConsumer(owner);
        Principal principal = setupPrincipal(new ConsumerPrincipal(c, owner));
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        assertThrows(ForbiddenException.class, () -> ownerResource.getOwner(owner.getKey()));
    }

    @Test
    public void testConsumerCanListPools() {
        Consumer c = createConsumer(owner);
        Principal principal = setupPrincipal(new ConsumerPrincipal(c, owner));
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        ownerResource.listOwnerPools(owner.getKey(), null, null, null, null, false, null,
            null, new ArrayList<>(), false, false, null, null, null, null, null, null);
    }

    @Test
    public void testUnmappedGuestConsumerCanListPoolsForFuture() {
        Consumer c = this.createConsumer(owner);
        c.setFact("virt.is_guest", "true");
        c.setFact("virt.uuid", "system_uuid");
        consumerCurator.merge(c);
        Principal principal = setupPrincipal(new ConsumerPrincipal(c, owner));

        securityInterceptor.enable();

        Date now = new Date();
        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute(Pool.Attributes.VIRT_ONLY, "true");
        pool1.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        pool1.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "false");
        pool1.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        pool1.setStartDate(now);
        pool1.setEndDate(new Date(now.getTime() + 1000L * 60 * 60 * 24 * 365));
        Pool pool2 = TestUtil.createPool(owner, p);
        pool2.setAttribute(Pool.Attributes.VIRT_ONLY, "true");
        pool2.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        pool2.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "false");
        pool2.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        pool2.setStartDate(new Date(now.getTime() + 2 * 1000L * 60 * 60 * 24 * 365));
        pool2.setEndDate(new Date(now.getTime() + 3 * 1000L * 60 * 60 * 24 * 365));
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        when(this.principalProvider.get()).thenReturn(principal);
        Stream<PoolDTO> result = ownerResource.listOwnerPools(owner.getKey(), c.getUuid(), null, null, null,
            false, Util.toDateTime(new Date()), null, new ArrayList<>(),
            false, false, null, null, null, null, null, null);

        assertNotNull(result);
        List<PoolDTO> nowList = result.collect(Collectors.toList());

        assertEquals(1, nowList.size());
        assert (nowList.get(0).getId().equals(pool1.getId()));

        Date activeOn = new Date(pool2.getStartDate().getTime() + 1000L * 60 * 60 * 24);
        result = ownerResource.listOwnerPools(owner.getKey(), c.getUuid(), null, null, null, false,
            Util.toDateTime(activeOn), null, new ArrayList<>(),
            false, false, null, null, null, null, null, null);

        assertNotNull(result);
        List<PoolDTO> futureList = result.collect(Collectors.toList());

        assertEquals(1, futureList.size());
        assert (futureList.get(0).getId().equals(pool2.getId()));
    }

    @Test
    public void testOwnerAdminCanGetPools() {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        when(this.principalProvider.get()).thenReturn(principal);

        Stream<PoolDTO> result = ownerResource.listOwnerPools(owner.getKey(), null, null, null, null, true,
            null, null, new ArrayList<>(), false, false, null, null, null, null, null, null);

        assertNotNull(result);
        List<PoolDTO> pools = result.collect(Collectors.toList());
        assertEquals(2, pools.size());
    }

    @Test
    public void testCanFilterPoolsByAttribute() throws Exception {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        poolCurator.create(pool1);

        Product p2 = TestUtil.createProduct();
        p2.setAttribute(Product.Attributes.CORES, "12");
        createProduct(p2, owner);
        Pool pool2 = TestUtil.createPool(owner, p2);
        poolCurator.create(pool2);

        List<String> params = List.of("cores:12");

        when(this.principalProvider.get()).thenReturn(principal);
        Stream<PoolDTO> result = ownerResource.listOwnerPools(owner.getKey(), null,
            null, null, null, true, null, null, params, false, false, null, null, null, null, null, null);

        assertNotNull(result);
        List<PoolDTO> pools = result.collect(Collectors.toList());

        assertEquals(1, pools.size());
        assertModelEqualsDTO(pool2, pools.get(0));

        params = List.of("virt_only:true");

        result = ownerResource.listOwnerPools(owner.getKey(), null, null,
            null, null, true, null, null, params, false, false, null, null, null, null, null, null);

        assertNotNull(result);
        pools = result.collect(Collectors.toList());

        assertEquals(1, pools.size());
        assertModelEqualsDTO(pool1, pools.get(0));
    }

    @Test
    public void testCanFilterOutDevPoolsByAttribute() {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = this.createProduct(owner);

        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        poolCurator.create(pool1);

        Product p2 = this.createProduct(owner);
        Pool pool2 = TestUtil.createPool(owner, p2);
        poolCurator.create(pool2);

        List<String> params = List.of();

        when(this.principalProvider.get()).thenReturn(principal);
        Stream<PoolDTO> result = ownerResource.listOwnerPools(owner.getKey(), null, null, null, null, true,
            null, null, params, false, false, null, null, null, null, null, null);

        assertNotNull(result);
        List<PoolDTO> pools = result.collect(Collectors.toList());

        assertEquals(2, pools.size());

        params = List.of(String.format("%s:!true", Pool.Attributes.DEVELOPMENT_POOL));

        result = ownerResource.listOwnerPools(owner.getKey(), null, null, null, null,
            true, null, null, params, false, false, null, null, null, null, null, null);

        assertNotNull(result);
        pools = result.collect(Collectors.toList());

        assertEquals(1, pools.size());
        assertModelEqualsDTO(pool2, pools.get(0));
    }

    private void assertModelEqualsDTO(Pool model, PoolDTO dto) {
        assertEquals(model.getId(), dto.getId());
        assertEquals(model.getType().toString(), dto.getType());
        assertEquals(model.getProductId(), dto.getProductId());
        assertEquals(model.getProductName(), dto.getProductName());
        assertEquals(model.getQuantity(), dto.getQuantity());
    }

    @Test
    public void ownerAdminCannotAccessAnotherOwnersPools() {
        Owner evilOwner = new Owner("evilowner");
        ownerCurator.create(evilOwner);
        Principal principal = setupPrincipal(evilOwner, Access.ALL);

        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        securityInterceptor.enable();
        OwnerResource ownerResource = this.buildOwnerResource();
        when(this.principalProvider.get()).thenReturn(principal);

        // Filtering should just cause this to return no results:
        assertThrows(NotFoundException.class, () ->
            ownerResource.listOwnerPools(owner.getKey(), null, null, null, null, true, null,
            null, new ArrayList<>(), false, false, null, null, null, null, null, null));
    }

    @Test
    public void testOwnerAdminCannotListAllOwners() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        assertThrows(ForbiddenException.class, () -> ownerResource.listOwners(null));
    }

    @Test
    public void testOwnerAdminCannotDelete() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        assertThrows(ForbiddenException.class, () -> ownerResource.deleteOwner(owner.getKey(), true, false));
    }

    @Test
    public void consumerCannotListAllConsumersInOwner() {
        Consumer c = createConsumer(owner);

        Principal principal = setupPrincipal(new ConsumerPrincipal(c, owner));
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        assertThrows(ForbiddenException.class, () -> ownerResource
            .listConsumers(owner.getKey(), null, null, new ArrayList<>(),
            null, null, null, null, null, null));
    }

    @Test
    public void consumerCanListConsumersByIdWhenOtherParametersPresent() {
        Consumer c = createConsumer(owner);
        List<String> uuids = new ArrayList<>();
        uuids.add(c.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        Set<String> types = new HashSet<>();
        types.add("type");
        consumerTypeCurator.create(new ConsumerType("type"));

        Stream<ConsumerDTOArrayElement> result = ownerResource
            .listConsumers(owner.getKey(), "username", types, uuids, null, null, null, null, null, null);

        assertNotNull(result);
        List<ConsumerDTOArrayElement> consumers = result.collect(Collectors.toList());

        assertEquals(0, consumers.size());
    }

    @Test
    public void consumerCannotListConsumersFromAnotherOwner() {
        Consumer c = createConsumer(owner);

        Owner owner2 = ownerCurator.create(new Owner("Owner2"));
        Consumer c2 = createConsumer(owner2);

        List<String> uuids = new ArrayList<>();
        uuids.add(c.getUuid());
        uuids.add(c2.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        Stream<ConsumerDTOArrayElement> result = ownerResource
            .listConsumers(owner.getKey(), null, null, uuids, null, null, null, null, null, null);

        assertNotNull(result);
        List<ConsumerDTOArrayElement> consumers = result.collect(Collectors.toList());

        assertEquals(1, consumers.size());
    }

    /**
     * I'm generally not a fan of testing this way, but in this case
     * I want to check that the exception message that is returned
     * correctly concats the invalid type name.
     */
    @Test
    public void failWhenListingByBadConsumerType() {
        Set<String> types = new HashSet<>();
        types.add("unknown");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> ownerResource
            .listConsumers(owner.getKey(), null, types, new ArrayList<>(),
            null, null, null, null, null, null));
        assertEquals("No such unit type(s): unknown", ex.getMessage());
    }

    @Test
    public void consumerCanListMultipleConsumers() {
        Consumer c = createConsumer(owner);
        Consumer c2 = createConsumer(owner);

        List<String> uuids = new ArrayList<>();
        uuids.add(c.getUuid());
        uuids.add(c2.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        Stream<ConsumerDTOArrayElement> result = ownerResource
            .listConsumers(owner.getKey(), null, null, uuids, null, null, null, null, null, null);

        assertNotNull(result);
        List<ConsumerDTOArrayElement> consumers = result.collect(Collectors.toList());

        assertEquals(2, consumers.size());
    }

    protected ConsumerType mockConsumerType() {
        int rnd = TestUtil.randomInt();
        ConsumerType ctype = new ConsumerType();
        ctype.setId("test_ctype-" + rnd);
        ctype.setLabel("ctype_label-" + rnd);

        doReturn(ctype).when(this.mockConsumerTypeCurator).getByLabel(eq(ctype.getLabel()));
        doReturn(ctype).when(this.mockConsumerTypeCurator).getByLabel(eq(ctype.getLabel()), anyBoolean());
        doReturn(ctype).when(this.mockConsumerTypeCurator).get(eq(ctype.getId()));

        doAnswer(new Answer<ConsumerType>() {
            @Override
            public ConsumerType answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Consumer consumer = (Consumer) args[0];
                ConsumerTypeCurator curator = (ConsumerTypeCurator) invocation.getMock();
                ConsumerType ctype = null;

                if (consumer == null || consumer.getTypeId() == null) {
                    throw new IllegalArgumentException("consumer is null or lacks a type ID");
                }

                ctype = curator.get(consumer.getTypeId());
                if (ctype == null) {
                    throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
                }

                return ctype;
            }
        }).when(this.mockConsumerTypeCurator).getConsumerType(any(Consumer.class));

        return ctype;
    }

    protected Owner mockOwner(String ownerKey) {
        int rnd = TestUtil.randomInt();

        Owner owner = new Owner()
            .setId("test-owner-" + rnd)
            .setKey(ownerKey)
            .setDisplayName("Test Owner " + rnd);

        doReturn(owner).when(this.mockOwnerCurator).getByKey(eq(owner.getKey()));
        return owner;
    }

    protected Consumer mockConsumer(Owner owner, ConsumerType ctype) {
        int rnd = TestUtil.randomInt();

        Consumer consumer = new Consumer()
            .setName("test_consumer-" + rnd)
            .setUsername("test_user-" + rnd)
            .setOwner(owner)
            .setType(ctype);

        consumer.setId("test_consumer-" + rnd);
        consumer.ensureUUID();

        doReturn(consumer).when(this.mockConsumerCurator).verifyAndLookupConsumer(eq(consumer.getUuid()));
        doReturn(consumer).when(this.mockConsumerCurator)
            .verifyAndLookupConsumerWithEntitlements(eq(consumer.getUuid()));

        return consumer;
    }

    @Test
    public void testListConsumersDoesNotRequirePagingForSmallResultSets() {
        ResteasyContext.pushContext(PageRequest.class, null);
        Owner owner = this.mockOwner("test_owner");
        ConsumerType ctype = this.mockConsumerType();

        List<Consumer> expected = Stream.generate(() -> this.mockConsumer(owner, ctype))
            .limit(5)
            .collect(Collectors.toList());

        doReturn(5L).when(this.mockConsumerCurator).getConsumerCount(any(ConsumerQueryArguments.class));
        doReturn(expected).when(this.mockConsumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        OwnerResource resource = this.buildOwnerResource();
        Stream<ConsumerDTOArrayElement> result = resource.listConsumers(owner.getKey(), "username", null,
            null, null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(expected.size(), result.count());
    }

    @Test
    public void testListConsumersRequiresPagingForLargeResultSets() {
        ResteasyContext.pushContext(PageRequest.class, null);
        String ownerKey = "test_owner";
        this.mockOwner(ownerKey);
        OwnerResource resource = this.buildOwnerResource();
        doReturn(5000L).when(this.mockConsumerCurator).getConsumerCount(any(ConsumerQueryArguments.class));

        assertThrows(BadRequestException.class, () ->
            resource.listConsumers(ownerKey, "username", null, null, null, null, null, null, null, null));
    }

    @Test
    public void testListConsumersByOwner() {
        Owner owner = this.mockOwner("test_owner");
        ConsumerType ctype = this.mockConsumerType();

        List<Consumer> expected = Stream.generate(() -> this.mockConsumer(owner, ctype))
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.mockConsumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        OwnerResource resource = this.buildOwnerResource();
        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = resource.listConsumers(owner.getKey(), null,
            null, null, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.mockConsumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertSame(owner, builder.getOwner());
        assertNull(builder.getUsername());
        assertNull(builder.getUuids());
        assertNull(builder.getTypes());
        assertNull(builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testListConsumersByUsername() {
        String username = "test_user";
        Owner owner = this.mockOwner("test_owner");
        ConsumerType ctype = this.mockConsumerType();

        List<Consumer> expected = Stream.generate(() -> this.mockConsumer(owner, ctype))
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.mockConsumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        OwnerResource resource = this.buildOwnerResource();
        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = resource.listConsumers(owner.getKey(), username, null,
            null, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.mockConsumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertEquals(owner, builder.getOwner());
        assertEquals(username, builder.getUsername());
        assertNull(builder.getUuids());
        assertNull(builder.getTypes());
        assertNull(builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testListConsumersByUuid() {
        List<String> uuids = Arrays.asList("uuid-1", "uuid-2", "uuid-3");
        Owner owner = this.mockOwner("test_owner");
        ConsumerType ctype = this.mockConsumerType();

        List<Consumer> expected = Stream.generate(() -> this.mockConsumer(owner, ctype))
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.mockConsumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        OwnerResource resource = this.buildOwnerResource();
        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = resource.listConsumers(owner.getKey(), null, null,
            uuids, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.mockConsumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertEquals(owner, builder.getOwner());
        assertNull(builder.getUsername());
        assertEquals(uuids, builder.getUuids());
        assertNull(builder.getTypes());
        assertNull(builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testListConsumersByType() {
        List<ConsumerType> types = Stream.generate(this::mockConsumerType)
            .limit(3)
            .collect(Collectors.toList());

        Map<String, ConsumerType> typeMap = types.stream()
            .collect(Collectors.toMap(ConsumerType::getLabel, Function.identity()));

        doAnswer(new Answer<List<ConsumerType>>() {
                @Override
                public List<ConsumerType> answer(InvocationOnMock iom) throws Throwable {
                    Set<String> labels = (Set<String>) iom.getArguments()[0];
                    List<ConsumerType> output = new ArrayList<>();

                    for (String label : labels) {
                        if (typeMap.containsKey(label)) {
                            output.add(typeMap.get(label));
                        }
                    }

                    return output;
                }
            }).when(this.mockConsumerTypeCurator).getByLabels(anySet());

        Owner owner = this.mockOwner("test_owner");
        ConsumerType ctype = this.mockConsumerType();

        List<Consumer> expected = Stream.generate(() -> this.mockConsumer(owner, ctype))
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.mockConsumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        OwnerResource resource = this.buildOwnerResource();
        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = resource.listConsumers(owner.getKey(), null,
            typeMap.keySet(), null, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.mockConsumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertEquals(owner, builder.getOwner());
        assertNull(builder.getUsername());
        assertNull(builder.getUuids());
        // We need an order-agnostic check here, and Hamcrest's matcher is busted, so we have to
        // do this one manually.
        assertTrue(Util.collectionsAreEqual(types, builder.getTypes()));
        assertNull(builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testListConsumersByHypervisorId() {
        List<String> hids = Arrays.asList("hypervisor-1", "hypervisor-2", "hypervisor-3");
        Owner owner = this.mockOwner("test_owner");
        ConsumerType ctype = this.mockConsumerType();

        List<Consumer> expected = Stream.generate(() -> this.mockConsumer(owner, ctype))
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.mockConsumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        OwnerResource resource = this.buildOwnerResource();
        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = resource.listConsumers(owner.getKey(), null, null,
            null, hids, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.mockConsumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertEquals(owner, builder.getOwner());
        assertNull(builder.getUsername());
        assertNull(builder.getUuids());
        assertNull(builder.getTypes());
        assertEquals(hids, builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testListConsumersByFact() {
        List<String> factsParam = List.of(
            "fact-1:value-1a",
            "fact-1:value-1b",
            "fact-2:value-2",
            "fact-3:value-3");

        Map<String, Collection<String>> factsMap = Map.of(
            "fact-1", Set.of("value-1a", "value-1b"),
            "fact-2", Set.of("value-2"),
            "fact-3", Set.of("value-3"));

        Owner owner = this.mockOwner("test_owner");
        ConsumerType ctype = this.mockConsumerType();

        List<Consumer> expected = Stream.generate(() -> this.mockConsumer(owner, ctype))
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.mockConsumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        OwnerResource resource = this.buildOwnerResource();
        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = resource.listConsumers(owner.getKey(), null, null,
            null, null, factsParam, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.mockConsumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertEquals(owner, builder.getOwner());
        assertNull(builder.getUsername());
        assertNull(builder.getUuids());
        assertNull(builder.getTypes());
        assertNull(builder.getHypervisorIds());
        assertEquals(factsMap, builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    //copied from consumerCannotListAllConsumersInOwner
    @Test
    public void consumerCannotCountAllConsumersInOwner() {
        Consumer c = createConsumer(owner);
        Principal principal = setupPrincipal(new ConsumerPrincipal(c, owner));
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        assertThrows(ForbiddenException.class, () ->
            ownerResource.countConsumers(owner.getKey(), null, null, null, null));
    }

    //copied from failWhenListingByBadConsumerType
    @Test
    public void failWhenCountingByBadConsumerType() {
        Set<String> types = new HashSet<>();
        types.add("unknown");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            ownerResource.countConsumers(owner.getKey(), null, types, null, null));

        assertEquals("No such unit type(s): unknown", ex.getMessage());
    }

    @Test
    public void countShouldThrowExceptionIfUnknownOwner() {
        String key = "unknown";
        createConsumer(owner);

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
            ownerResource.countConsumers(key, null, null, null, null));
        assertEquals(i18n.tr("Owner with key \"{0}\" was not found", key), ex.getMessage());
    }

    @Test
    public void consumerListPoolsGetCalculatedAttributes() {
        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);

        Consumer c = this.createConsumer(owner);

        Principal principal = setupPrincipal(new ConsumerPrincipal(c, owner));
        when(this.principalProvider.get()).thenReturn(principal);

        securityInterceptor.enable();

        Stream<PoolDTO> poolStream = ownerResource.listOwnerPools(owner.getKey(), c.getUuid(), null,
            p.getId(), null, true, null, null, new ArrayList<>(),
            false, false, null, null, null, null, null, null);

        assertNotNull(poolStream);
        List<PoolDTO> pools = poolStream.collect(Collectors.toList());

        assertEquals(1, pools.size());
        PoolDTO returnedPool = pools.get(0);
        assertNotNull(returnedPool.getCalculatedAttributes());
    }

    @Test
    public void testConsumerListPoolsCannotAccessOtherConsumer() {
        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);

        Consumer c = createConsumer(owner);

        securityInterceptor.enable();

        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        Principal principal = setupPrincipal(owner2, Access.NONE);
        when(this.principalProvider.get()).thenReturn(principal);

        assertThrows(NotFoundException.class, () -> ownerResource.listOwnerPools(
            owner.getKey(), c.getUuid(), null, p.getUuid(),  null, true, null, null,
            new ArrayList<>(), false, false, null, null, null, null, null, null)
        );
    }

    @Test
    public void testActivationKeyCreateRead() {
        ActivationKeyDTO key = new ActivationKeyDTO()
            .name("dd")
            .releaseVer(new ReleaseVerDTO().releaseVer("release1"));

        key = ownerResource.createActivationKey(owner.getKey(), key);
        assertNotNull(key.getId());
        assertEquals(key.getOwner().getId(), owner.getId());
        assertEquals(key.getReleaseVer().getReleaseVer(), "release1");
        CandlepinQuery<ActivationKeyDTO> result = ownerResource.ownerActivationKeys(owner.getKey(), null);

        assertNotNull(result);
        List<ActivationKeyDTO> keys = result.list();

        assertEquals(1, keys.size());
    }

    @Test
    public void testSearchActivationsKeysByName() {
        ActivationKeyDTO key = new ActivationKeyDTO()
            .name("dd")
            .releaseVer(new ReleaseVerDTO().releaseVer("release1"));
        key = ownerResource.createActivationKey(owner.getKey(), key);
        assertNotNull(key.getId());
        assertEquals(key.getOwner().getId(), owner.getId());
        assertEquals(key.getReleaseVer().getReleaseVer(), "release1");

        key = new ActivationKeyDTO()
            .name("blah")
            .releaseVer(new ReleaseVerDTO().releaseVer("release2"));
        key = ownerResource.createActivationKey(owner.getKey(), key);
        assertNotNull(key.getId());
        assertEquals(key.getOwner().getId(), owner.getId());
        assertEquals(key.getReleaseVer().getReleaseVer(), "release2");

        CandlepinQuery<ActivationKeyDTO> result = ownerResource.ownerActivationKeys(owner.getKey(), "dd");
        assertNotNull(result);
        List<ActivationKeyDTO> keys = result.list();
        assertEquals(1, keys.size());

        result = ownerResource.ownerActivationKeys(owner.getKey(), null);
        assertNotNull(result);
        keys = result.list();
        assertEquals(2, keys.size());
    }

    @Test
    public void testActivationKeyRequiresName() {
        OwnerCurator oc = mock(OwnerCurator.class);
        Owner o = new Owner();
        o.setKey("owner-key");

        when(this.mockOwnerCurator.getByKey(anyString())).thenReturn(o);

        OwnerResource resource = this.buildOwnerResource();

        ActivationKeyDTO key = new ActivationKeyDTO();
        assertThrows(BadRequestException.class, () -> resource.createActivationKey(owner.getKey(), key));
    }

    @Test
    public void testActivationKeyTooLongRelease() {
        Owner o = new Owner();
        o.setKey("owner-key");

        when(this.mockOwnerCurator.getByKey(anyString())).thenReturn(o);

        OwnerResource resource = this.buildOwnerResource();

        ActivationKeyDTO key = new ActivationKeyDTO();
        key.releaseVer(new ReleaseVerDTO().releaseVer(TestUtil.getStringOfSize(256)));

        assertThrows(BadRequestException.class, () -> resource.createActivationKey(owner.getKey(), key));
    }

    @Test
    public void testValidationCreateActivationKeyWithEmptyProductId() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ActivationKeyProductDTO> products = new HashSet<>();
        products.add(new ActivationKeyProductDTO().productId(""));
        key.products(products);

        assertThrows(IllegalArgumentException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithNullProduct() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ActivationKeyProductDTO> products = new HashSet<>();
        products.add(null);
        key.products(products);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithNullPoolId() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(new ActivationKeyPoolDTO().poolId(null));
        key.pools(pools);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithEmptyPoolId() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(new ActivationKeyPoolDTO().poolId(""));
        key.pools(pools);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithNullPool() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(null);
        key.pools(pools);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithNullContentOverrideName() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(new ContentOverrideDTO().name(null).contentLabel("a label"));
        key.contentOverrides(contentOverrideDTOS);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithEmptyContentOverrideName() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(new ContentOverrideDTO().name("").contentLabel("a label"));
        key.contentOverrides(contentOverrideDTOS);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithNullContentOverrideLabel() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(new ContentOverrideDTO().name("a name").contentLabel(null));
        key.contentOverrides(contentOverrideDTOS);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithEmptyContentOverrideLabel() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(new ContentOverrideDTO().name("a name").contentLabel(""));
        key.contentOverrides(contentOverrideDTOS);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateActivationKeyWithNullContentOverride() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(null);
        key.contentOverrides(contentOverrideDTOS);

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void ownerWithParentOwnerCanBeCreated() {
        OwnerDTO parent = new OwnerDTO();
        parent.setKey("parent");
        parent.setDisplayName("parent");

        OwnerDTO child = new OwnerDTO();
        child.setKey("child");
        child.setDisplayName("child");
        child.setParentOwner(new NestedOwnerDTO().id(parent.getId()).key(parent.getKey())
            .displayName(parent.getDisplayName()));

        OwnerDTO pout = this.ownerResource.createOwner(parent);

        assertNotNull(pout);
        assertNotNull(pout.getId());
        assertNotNull(this.ownerCurator.get(pout.getId()));

        OwnerDTO cout = this.ownerResource.createOwner(child);

        assertNotNull(cout);
        assertNotNull(cout.getId());

        Owner owner = this.ownerCurator.get(cout.getId());

        assertNotNull(owner);
        assertNotNull(owner.getParentOwner());

        assertEquals(pout.getId(), owner.getParentOwner().getId());
    }

    @Test
    public void ownerWithInvalidParentIdCannotBeCreated() {
        OwnerDTO child = new OwnerDTO();
        child.setKey("child");
        child.setDisplayName("child");

        NestedOwnerDTO parent = new NestedOwnerDTO();
        parent.setId("parent");
        parent.setDisplayName("parent");

        child.setParentOwner(parent);

        assertThrows(NotFoundException.class, () -> this.ownerResource.createOwner(child));
    }

    @Test
    public void ownerWithInvalidParentKeyCannotBeCreated() {
        OwnerDTO child = new OwnerDTO();
        child.setKey("child");
        child.setDisplayName("child");

        NestedOwnerDTO parent = new NestedOwnerDTO();
        parent.setKey("parent");
        parent.setDisplayName("parent");

        child.setParentOwner(parent);

        assertThrows(NotFoundException.class, () -> this.ownerResource.createOwner(child));
    }

    @Test
    public void ownerWithInvalidParentWhoseIdAndKeyIsNullCannotBeCreated() {
        OwnerDTO child = new OwnerDTO();
        child.setKey("child");
        child.setDisplayName("child");

        NestedOwnerDTO parent = new NestedOwnerDTO();
        parent.setDisplayName("parent");

        child.setParentOwner(parent);

        assertThrows(NotFoundException.class, () -> this.ownerResource.createOwner(child));
    }

    @Test
    public void cleanupWithOutstandingPermissions() {
        PermissionBlueprint p = new PermissionBlueprint(PermissionType.OWNER, owner, Access.ALL);
        Role r = new Role("rolename");
        r.addPermission(p);
        roleCurator.create(r);
        ownerResource.deleteOwner(owner.getKey(), false,  false);
    }

    @Test
    public void undoImportforOwnerWithNoImports() {
        OwnerDTO dto = new OwnerDTO();
        dto.setKey("owner-with-no-imports");
        dto.setDisplayName("foo");

        dto = ownerResource.createOwner(dto);
        OwnerDTO finalDto = dto;
        assertThrows(NotFoundException.class, () ->
            ownerResource.undoImports(finalDto.getKey())
        );
    }

    @Test
    public void testConflictOnDelete() {
        Owner o = mock(Owner.class);

        when(this.mockOwnerCurator.getByKey(eq("testOwner"))).thenReturn(o);
        ConstraintViolationException ce = new ConstraintViolationException(null, null, null);
        PersistenceException pe = new PersistenceException(ce);
        Mockito.doThrow(pe).when(this.mockOwnerManager).cleanupAndDelete(eq(o), eq(true));

        OwnerResource resource = this.buildOwnerResource();

        assertThrows(ConflictException.class, () -> resource.deleteOwner("testOwner",
            true, true));
    }

    @Test
    public void testActivationKeyNameUnique() {
        ActivationKeyDTO ak = mock(ActivationKeyDTO.class);
        ActivationKey akOld = mock(ActivationKey.class);
        Owner o = mock(Owner.class);

        when(ak.getName()).thenReturn("testKey");
        when(this.mockActivationKeyCurator.getByKeyName(eq(o), eq("testKey"))).thenReturn(akOld);
        when(this.mockOwnerCurator.getByKey(eq("testOwner"))).thenReturn(o);

        OwnerResource resource = this.buildOwnerResource();

        assertThrows(BadRequestException.class, () -> resource.createActivationKey("testOwner", ak));
    }

    @Test
    public void testUpdateOwner() {
        config.setProperty(ConfigProperties.STANDALONE, "false");
        Owner owner = new Owner("Test Owner", "test");
        ownerCurator.create(owner);

        Product prod1 = TestUtil.createProduct()
            .setAttribute(Product.Attributes.SUPPORT_LEVEL, "premium");

        Product prod2 = TestUtil.createProduct()
            .setAttribute(Product.Attributes.SUPPORT_LEVEL, "standard");

        this.createProduct(prod1, owner);
        this.createProduct(prod2, owner);

        Pool pool1 = TestUtil.createPool(owner, prod1)
            .setQuantity(2000L)
            .setStartDate(TestUtil.createDateOffset(-2, 0, 0))
            .setEndDate(TestUtil.createDateOffset(2, 0, 0));

        Pool pool2 = TestUtil.createPool(owner, prod2)
            .setQuantity(2000L)
            .setStartDate(TestUtil.createDateOffset(-2, 0, 0))
            .setEndDate(TestUtil.createDateOffset(2, 0, 0));

        this.poolCurator.create(pool1);
        this.poolCurator.create(pool2);
        this.poolCurator.flush();

        owner.setDefaultServiceLevel("premium");
        Owner parentOwner1 = ownerCurator.create(new Owner("Paren Owner 1", "parentTest1"));
        Owner parentOwner2 = ownerCurator.create(new Owner("Paren Owner 2", "parentTest2"));
        owner.setParentOwner(parentOwner1);

        ownerCurator.merge(owner);
        ownerCurator.flush();

        // Update with Display Name Only
        OwnerDTO dto = new OwnerDTO();
        dto.setDisplayName("New Name");

        ownerResource.updateOwner(owner.getKey(), dto);
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner1, owner.getParentOwner());
        assertEquals("premium", owner.getDefaultServiceLevel());
        assertFalse(owner.isAutobindDisabled());

        // Update with Default Service Level only
        dto = new OwnerDTO();
        dto.setDefaultServiceLevel("standard");

        ownerResource.updateOwner(owner.getKey(), dto);
        assertEquals("standard", owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner1, owner.getParentOwner());
        assertFalse(owner.isAutobindDisabled());

        // Update with Parent Owner only
        NestedOwnerDTO parentDto = new NestedOwnerDTO();
        parentDto.setId(parentOwner2.getId());

        dto = new OwnerDTO();
        dto.setParentOwner(parentDto);

        ownerResource.updateOwner(owner.getKey(), dto);
        assertEquals(parentOwner2, owner.getParentOwner());
        assertEquals("standard", owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertFalse(owner.isAutobindDisabled());

        // Update with empty Service Level only
        dto = new OwnerDTO();
        dto.setDefaultServiceLevel("");

        ownerResource.updateOwner(owner.getKey(), dto);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
        assertFalse(owner.isAutobindDisabled());

        // Update autobind with disabled value.
        dto = new OwnerDTO();
        dto.setAutobindDisabled(true);

        ownerResource.updateOwner(owner.getKey(), dto);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
        assertTrue(owner.isAutobindDisabled());

        // Update autobind with enabled value.
        dto = new OwnerDTO();
        dto.setAutobindDisabled(false);

        ownerResource.updateOwner(owner.getKey(), dto);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
        assertFalse(owner.isAutobindDisabled());

        // Unset autobindDisabled results in no update.
        dto = new OwnerDTO();
        dto.setAutobindDisabled(null);

        ownerResource.updateOwner(owner.getKey(), dto);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
        assertFalse(owner.isAutobindDisabled());
    }

    @Test
    public void testImportManifestAsyncSuccess() throws IOException, ImporterException, JobException {
        OwnerResource thisOwnerResource = new OwnerResource(
            this.mockOwnerCurator, null, null, i18n, this.mockEventSink, mockEventFactory, null,
            this.mockManifestManager, null, null, null, null, importRecordCurator, null, null, null, null,
            null, contentOverrideValidator, serviceLevelValidator, null, null, null, null,
            this.modelTranslator, this.mockJobManager, null, null);

        MultipartInput input = mock(MultipartInput.class);
        InputPart part = mock(InputPart.class);
        File archive = mock(File.class);
        List<InputPart> parts = new ArrayList<>();
        parts.add(part);
        MultivaluedMap<String, String> mm = new MultivaluedMapImpl<>();
        List<String> contDis = new ArrayList<>();
        contDis.add("form-data; name=\"upload\"; filename=\"test_file.zip\"");
        mm.put("Content-Disposition", contDis);
        Owner owner = new Owner();
        String ownerKey = "random-owner-key";
        owner.setKey(ownerKey);

        AsyncJobStatus asyncJobStatus = new AsyncJobStatus();
        asyncJobStatus.setName(ImportJob.JOB_NAME);

        JobConfig job = new JobConfig();
        job.setJobName(ImportJob.JOB_NAME);

        when(input.getParts()).thenReturn(parts);
        when(part.getHeaders()).thenReturn(mm);
        when(part.getBody(any(GenericType.class))).thenReturn(archive);
        when(this.mockManifestManager.importManifestAsync(eq(owner), any(File.class), eq("test_file.zip"),
            any(ConflictOverrides.class))).thenReturn(job);
        when(this.mockOwnerCurator.getByKey(anyString())).thenReturn(owner);
        when(this.mockJobManager.queueJob(eq(job))).thenReturn(asyncJobStatus);

        AsyncJobStatusDTO dto = thisOwnerResource
            .importManifestAsync(owner.getKey(), new ArrayList<>(), input);
        assertNotNull(dto);
        assertEquals(job.getJobName(), dto.getName());

        verify(this.mockManifestManager, never()).importManifest(eq(owner), any(File.class),
            any(String.class), any(ConflictOverrides.class));
    }

    @Test
    public void upstreamConsumers() {
        Owner owner = mock(Owner.class);
        Principal p = mock(Principal.class);
        UpstreamConsumer upstream = mock(UpstreamConsumer.class);

        OwnerResource resource = this.buildOwnerResource();

        when(this.mockOwnerCurator.getByKey(eq("admin"))).thenReturn(owner);
        when(owner.getUpstreamConsumer()).thenReturn(upstream);
        when(this.principalProvider.get()).thenReturn(p);
        List<UpstreamConsumerDTOArrayElement> results = resource.getUpstreamConsumers("admin");

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    public void testSetAndDeleteOwnerLogLevel() {
        Owner owner = new Owner("Test Owner", "test");
        ownerCurator.create(owner);
        ownerResource.setLogLevel(owner.getKey(), "ALL");

        owner = ownerCurator.getByKey(owner.getKey());
        assertEquals(owner.getLogLevel(), "ALL");

        ownerResource.deleteLogLevel(owner.getKey());
        owner = ownerCurator.getByKey(owner.getKey());
        assertNull(owner.getLogLevel());
    }

    @Test
    public void testSetBadLogLevel() {
        Owner owner = new Owner("Test Owner", "test");
        ownerCurator.create(owner);
        assertThrows(BadRequestException.class, () ->
            ownerResource.setLogLevel(owner.getKey(), "THISLEVELISBAD")
        );
    }

    @Test
    public void createPool() {
        Product prod = this.createProduct(owner);
        Pool pool = TestUtil.createPool(owner, prod);
        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);

        assertEquals(0, poolCurator.listByOwner(owner).list().size());
        PoolDTO createdPoolDto = ownerResource.createPool(owner.getKey(), poolDto);
        assertEquals(1, poolCurator.listByOwner(owner).list().size());
        assertNotNull(createdPoolDto.getId());
    }

    @Test
    public void updatePool() {
        Product prod = this.createProduct(owner);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setSubscriptionSubKey(PRIMARY_POOL_SUB_KEY);
        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);

        poolDto = ownerResource.createPool(owner.getKey(), poolDto);
        List<Pool> createdPools = poolCurator.listByOwner(owner).list();
        assertEquals(1, createdPools.size());
        assertEquals(pool.getQuantity(), createdPools.get(0).getQuantity());

        poolDto.setQuantity(10L);
        ownerResource.updatePool(owner.getKey(), poolDto);
        List<Pool> updatedPools = poolCurator.listByOwner(owner).list();
        assertEquals(1, updatedPools.size());
        assertEquals(10L, updatedPools.get(0).getQuantity().longValue());
    }

    @Test
    public void createBonusPool() {
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "2");
        createProduct(prod, owner);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setUpstreamPoolId("upstream-" + pool.getId());
        assertEquals(0, poolCurator.listByOwner(owner).list().size());

        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);
        ownerResource.createPool(owner.getKey(), poolDto);

        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(2, pools.size());
        assertTrue(pools.get(0).getSubscriptionSubKey().startsWith(PRIMARY_POOL_SUB_KEY) ||
            pools.get(1).getSubscriptionSubKey().startsWith(PRIMARY_POOL_SUB_KEY));
        assertTrue(pools.get(0).getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY) ||
            pools.get(1).getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY));
    }

    @Test
    public void createBonusPoolForUpdate() {
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        createProduct(prod, owner);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setUpstreamPoolId("upstream-" + pool.getId());
        pool.setSubscriptionSubKey(PRIMARY_POOL_SUB_KEY);
        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);
        poolDto = ownerResource.createPool(owner.getKey(), poolDto);

        poolDto.setQuantity(100L);
        ownerResource.updatePool(owner.getKey(), poolDto);

        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(2, pools.size());
        assertTrue(pools.get(0).getSubscriptionSubKey().startsWith(PRIMARY_POOL_SUB_KEY) ||
            pools.get(1).getSubscriptionSubKey().startsWith(PRIMARY_POOL_SUB_KEY));
        assertTrue(pools.get(0).getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY) ||
            pools.get(1).getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY));
        assertEquals(100L, pools.get(0).getQuantity().longValue());
        assertEquals(300L, pools.get(1).getQuantity().longValue());
    }

    @Test
    public void removePoolsForExpiredUpdate() {
        Product prod = new Product("foo", "bar");
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        this.createProduct(prod, owner);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setUpstreamPoolId("upstream-" + pool.getId());
        pool.setSubscriptionSubKey(PRIMARY_POOL_SUB_KEY);
        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);
        poolDto = ownerResource.createPool(owner.getKey(), poolDto);

        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(2, pools.size());

        poolDto.setStartDate(Util.toDateTime(new Date(System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000)));
        poolDto.setEndDate(Util.toDateTime(new Date(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000)));
        ownerResource.updatePool(owner.getKey(), poolDto);
        pools = poolCurator.listByOwner(owner).list();
        assertEquals(0, pools.size());
    }

    @Test
    public void cantUpdateBonusPool() {
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        createProduct(prod, owner);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setUpstreamPoolId("upstream-" + pool.getId());
        pool.setSubscriptionSubKey(PRIMARY_POOL_SUB_KEY);
        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);
        ownerResource.createPool(owner.getKey(), poolDto);
        List<Pool> pools = poolCurator.listByOwner(owner).list();

        Pool bonusPool = null;
        for (Pool p : pools) {
            if (p.getSubscriptionSubKey().contentEquals(DERIVED_POOL_SUB_KEY)) {
                bonusPool = p;
            }
        }
        assertNotNull(bonusPool);

        poolDto = this.modelTranslator.translate(bonusPool, PoolDTO.class);
        PoolDTO finalPoolDto = poolDto;
        assertThrows(BadRequestException.class, () ->
            ownerResource.updatePool(owner.getKey(), finalPoolDto)
        );
    }

    @Test
    public void enrichPool() {
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        prod.setMultiplier(2L);
        createProduct(prod, owner);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setQuantity(100L);
        assertEquals(0, poolCurator.listByOwner(owner).list().size());

        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);
        ownerResource.createPool(owner.getKey(), poolDto);
        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(1, pools.size());
        assertTrue(Boolean.parseBoolean(pools.get(0).getAttributeValue(Product.Attributes.VIRT_ONLY)));
        assertEquals(200L, pools.get(0).getQuantity().intValue());
    }

    @Test
    public void getAllEntitlementsForOwner() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Pool pool = this.createPool(owner, this.createProduct());
        Entitlement e = this.createEntitlement(owner, consumer, pool, null);

        List<Entitlement> entitlements = new ArrayList<>();
        entitlements.add(e);
        Page<List<Entitlement>> page = new Page<>();
        page.setPageData(entitlements);

        ResteasyContext.pushContext(PageRequest.class, req);

        List<EntitlementDTO> result = this.ownerResource
            .ownerEntitlements(owner.getKey(), null, null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals(e.getId(), result.get(0).getId());
    }

    @Test
    public void getEntitlementsForNonExistantOwner() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        ResteasyContext.pushContext(PageRequest.class, req);

        OwnerResource resource = this.buildOwnerResource();

        assertThrows(NotFoundException.class, () ->
            resource.ownerEntitlements("Taylor Swift", null, null, null, null, null, null)
        );
    }

    @Test
    public void testCreateUeberCertificateFromScratch() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        Owner owner = TestUtil.createOwner();
        UeberCertificate entCert = mock(UeberCertificate.class);

        when(this.mockOwnerCurator.getByKey(eq("admin")))
            .thenReturn(owner);

        when(this.mockUeberCertificateGenerator.generate(eq(owner.getKey()), eq(principal)))
            .thenReturn(entCert);
        when(this.principalProvider.get()).thenReturn(principal);

        OwnerResource resource = this.buildOwnerResource();

        UeberCertificateDTO expected = this.modelTranslator.translate(entCert, UeberCertificateDTO.class);
        UeberCertificateDTO result = resource.createUeberCertificate(owner.getKey());
        assertEquals(expected, result);
    }

    @Test
    public void testCreateUeberCertificateRegenerate() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        Owner owner = TestUtil.createOwner();
        UeberCertificate entCert = mock(UeberCertificate.class);

        OwnerResource resource = this.buildOwnerResource();

        when(this.mockUeberCertificateGenerator.generate(eq(owner.getKey()), eq(principal)))
            .thenReturn(entCert);
        when(this.principalProvider.get()).thenReturn(principal);

        UeberCertificateDTO expected = this.modelTranslator.translate(entCert, UeberCertificateDTO.class);
        UeberCertificateDTO result = resource.createUeberCertificate(owner.getKey());

        assertEquals(expected, result);
    }

    @Test
    public void testReturnProductSysPurposeValuesForOwner() throws Exception {
        Owner owner = TestUtil.createOwner();

        OwnerResource resource = this.buildOwnerResource();

        when(this.mockOwnerCurator.getByKey(eq(owner.getKey())))
            .thenReturn(owner);

        Map<String, Set<String>> mockMap = new HashMap<>();
        when(this.mockOwnerProductCurator.getSyspurposeAttributesByOwner(eq(owner)))
            .thenReturn(mockMap);

        Set<String> mockAddons = new HashSet<>();
        mockAddons.add("hello");
        mockAddons.add("world");
        mockAddons.add("earth");
        mockMap.put(SystemPurposeAttributeType.ADDONS.toString(), mockAddons);

        Set<String> mockUsage = new HashSet<>();
        mockUsage.add("production");
        mockUsage.add("production");
        mockUsage.add("development");
        mockMap.put(SystemPurposeAttributeType.USAGE.toString(), mockUsage);

        SystemPurposeAttributesDTO result = resource.getSyspurpose(owner.getKey());

        assertEquals(modelTranslator.translate(owner, NestedOwnerDTO.class), result.getOwner());
        Set<String> addons =
            result.getSystemPurposeAttributes().get(SystemPurposeAttributeType.ADDONS.toString());
        Set<String> expectedAddOns = new HashSet<>(Arrays.asList("hello", "earth", "world"));
        assertEquals(expectedAddOns, addons);

        Set<String> usage = result.getSystemPurposeAttributes()
            .get(SystemPurposeAttributeType.USAGE.toString());
        Set<String> expectedUsage = new HashSet<>(Arrays.asList("production", "development"));
        assertEquals(expectedUsage, usage);
    }

    @Test
    public void testReturnNoProductSysPurposeValuesForOwner() throws Exception {
        Owner owner = TestUtil.createOwner();
        OwnerResource resource = this.buildOwnerResource();
        when(this.mockOwnerCurator.getByKey(eq(owner.getKey())))
                .thenReturn(owner);

        Map<String, Set<String>> mockMap = new HashMap<>();
        when(this.mockOwnerProductCurator.getSyspurposeAttributesByOwner(eq(owner)))
                .thenReturn(mockMap);
        SystemPurposeAttributesDTO result = resource.getSyspurpose(owner.getKey());
        Map<String, Set<String>> returned = result.getSystemPurposeAttributes();
        assertEquals(returned, new HashMap<>());
    }

    @Test
    public void testReturnProductSysPurposeValuesForNoOwnerForId() throws Exception {
        OwnerResource resource = this.buildOwnerResource();
        when(this.mockOwnerCurator.getByKey(any(String.class))).thenReturn(null);
        assertThrows(NotFoundException.class, () -> resource.getSyspurpose(owner.getKey()));
    }

    @Test
    public void testReturnProductSysPurposeValuesForNoOwnerId() throws Exception {
        OwnerResource resource = this.buildOwnerResource();
        assertThrows(BadRequestException.class, () -> resource.getSyspurpose(""));
    }

    @Test
    public void testReturnConsumerSysPurposeValuesForOwner() throws Exception {
        Owner owner = TestUtil.createOwner();

        when(this.mockOwnerCurator.getByKey(eq(owner.getKey())))
            .thenReturn(owner);

        List<String> mockRoles = new ArrayList<>();
        mockRoles.add("role1");
        mockRoles.add("role2");
        mockRoles.add("role3");

        List<String> mockUsages = new ArrayList<>();
        mockUsages.add("usage1");
        mockUsages.add("usage2");
        mockUsages.add("usage3");

        List<String> mockSLAs = new ArrayList<>();
        mockSLAs.add("sla1");
        mockSLAs.add("sla2");
        mockSLAs.add("sla3");

        List<String> mockAddons = new ArrayList<>();
        mockAddons.add("addon1");
        mockAddons.add("addon2");
        mockAddons.add("addon3");

        List<String> mockServiceType = new ArrayList<>();
        mockServiceType.add("serviceType1");
        mockServiceType.add("serviceType2");
        mockServiceType.add("serviceType3");

        when(this.mockConsumerCurator.getDistinctSyspurposeValuesByOwner(eq(owner),
            eq(SystemPurposeAttributeType.ROLES))).thenReturn(mockRoles);
        when(this.mockConsumerCurator.getDistinctSyspurposeValuesByOwner(eq(owner),
            eq(SystemPurposeAttributeType.SERVICE_LEVEL))).thenReturn(mockSLAs);
        when(this.mockConsumerCurator.getDistinctSyspurposeValuesByOwner(eq(owner),
            eq(SystemPurposeAttributeType.USAGE))).thenReturn(mockUsages);
        when(this.mockConsumerCurator.getDistinctSyspurposeAddonsByOwner(eq(owner)))
            .thenReturn(mockAddons);
        when(this.mockConsumerCurator.getDistinctSyspurposeValuesByOwner(eq(owner),
            eq(SystemPurposeAttributeType.SERVICE_TYPE))).thenReturn(mockServiceType);

        OwnerResource resource = this.buildOwnerResource();

        SystemPurposeAttributesDTO result = resource.getConsumersSyspurpose(owner.getKey());

        assertEquals(modelTranslator.translate(owner, NestedOwnerDTO.class), result.getOwner());
        Set<String> addons =
            result.getSystemPurposeAttributes().get(SystemPurposeAttributeType.ADDONS.toString());
        Set<String> expectedAddOns = new HashSet<>(Arrays.asList("addon1", "addon2", "addon3"));
        assertEquals(expectedAddOns, addons);

        Set<String> usage = result.getSystemPurposeAttributes()
            .get(SystemPurposeAttributeType.USAGE.toString());

        Set<String> expectedUsage = new HashSet<>(Arrays.asList("usage1", "usage2", "usage3"));
        assertEquals(expectedUsage, usage);

        Set<String> roles = result.getSystemPurposeAttributes()
            .get(SystemPurposeAttributeType.ROLES.toString());
        Set<String> expectedRoles = new HashSet<>(Arrays.asList("role1", "role2", "role3"));
        assertEquals(expectedRoles, roles);

        Set<String> serviceLevelAgreements = result.getSystemPurposeAttributes()
            .get(SystemPurposeAttributeType.SERVICE_LEVEL.toString());
        Set<String> expectedSLAs = new HashSet<>(Arrays.asList("sla1", "sla2", "sla3"));
        assertEquals(expectedSLAs, serviceLevelAgreements);

        Set<String> serviceType = result.getSystemPurposeAttributes()
            .get(SystemPurposeAttributeType.SERVICE_TYPE.toString());

        Set<String> expectedServiceType = Util.asSet("serviceType1",
            "serviceType2", "serviceType3");
        assertEquals(expectedServiceType, serviceType);
    }

    @Test
    public void testCreateOwnerSetContentAccessListAndMode() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode + "," + orgEnvMode)
            .contentAccessMode(orgEnvMode);

        OwnerDTO output = this.ownerResource.createOwner(changes);

        assertNotNull(output);

        assertEquals(entitlementMode + "," + orgEnvMode, output.getContentAccessModeList());
        assertEquals(orgEnvMode, output.getContentAccessMode());
    }

    @Test
    public void testCreateOwnerSetContentAccessList() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode + "," + orgEnvMode);

        OwnerDTO output = this.ownerResource.createOwner(changes);

        assertNotNull(output);
        assertEquals(entitlementMode + "," + orgEnvMode, output.getContentAccessModeList());
        assertEquals(orgEnvMode, output.getContentAccessMode());
    }

    @Test
    public void testCreateOwnerSetContentAccessModeCannotUseInvalidModeInList() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode + ",unsupported_mode");

        assertThrows(BadRequestException.class, () -> ownerResource.createOwner(changes));
    }

    @Test
    public void testCreateOwnerSetContentAccessModeCannotUseInvalidMode() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode)
            .contentAccessMode(orgEnvMode);

        assertThrows(BadRequestException.class, () -> ownerResource.createOwner(changes));
    }

    @Test
    public void testCreateOwnerSetContentAccessModeCannotUseImpliedDefault() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode);

        // This should fail since creation requires that we specify the values, even if those
        // values are defaults. As such, the default value of entitlement should not be allowed
        // at this point.

        assertThrows(BadRequestException.class, () -> ownerResource.createOwner(changes));
    }

    @Test
    public void testCreateOwnerCanUseNonDefaultContentAccessMode() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode)
            .contentAccessMode(entitlementMode);

        OwnerDTO output = ownerResource.createOwner(changes);
        assertNotNull(output);
    }

    @Test
    public void testCreateOwnerCanSetNonDefaultContentAccessModeList() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode + "," + orgEnvMode);

        OwnerDTO output = this.ownerResource.createOwner(changes);

        assertNotNull(output);

        assertEquals(entitlementMode + "," + orgEnvMode, output.getContentAccessModeList());
        assertEquals(orgEnvMode, output.getContentAccessMode());
    }

    @Test
    public void testSetContentAccessModeList() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessModeList(entitlementMode + "," + orgEnvMode);

        OwnerDTO output = this.ownerResource.updateOwner(owner.getKey(), changes);

        assertNotNull(output);

        assertEquals(entitlementMode + "," + orgEnvMode, output.getContentAccessModeList());
        assertEquals(entitlementMode, output.getContentAccessMode());
    }

    @Test
    public void testSetContentAccessMode() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode + "," + orgEnvMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessMode(orgEnvMode);

        OwnerDTO output = this.ownerResource.updateOwner(owner.getKey(), changes);

        assertNotNull(output);

        assertEquals(entitlementMode + "," + orgEnvMode, output.getContentAccessModeList());
        assertEquals(orgEnvMode, output.getContentAccessMode());
    }

    @Test
    public void testSetContentAccessModeListAndMode() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessModeList(entitlementMode + "," + orgEnvMode)
            .contentAccessMode(orgEnvMode);

        OwnerDTO output = this.ownerResource.updateOwner(owner.getKey(), changes);

        assertNotNull(output);

        assertEquals(entitlementMode + "," + orgEnvMode, output.getContentAccessModeList());
        assertEquals(orgEnvMode, output.getContentAccessMode());
    }

    @Test
    public void testSetContentAccessModeCannotUseInvalidModeInList() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessModeList(entitlementMode + ",unsupported_mode");

        assertThrows(BadRequestException.class, () -> ownerResource.updateOwner(owner.getKey(), changes));
    }

    @Test
    public void testSetContentAccessModeCannotUseInvalidMode() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessMode(orgEnvMode);

        assertThrows(BadRequestException.class, () -> ownerResource.updateOwner(owner.getKey(), changes));
    }

    @Test
    public void testUpdateCannotChangeContentAccessModeIfNotPresentInContentAccessList() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessMode(orgEnvMode);

        assertThrows(BadRequestException.class, () -> ownerResource.updateOwner(owner.getKey(), changes));
    }

    @Test
    void shouldThrowWhenOwnerNotFound() {
        when(mockOwnerCurator.getOwnerContentAccess(anyString()))
            .thenThrow(OwnerNotFoundException.class);

        OwnerResource resource = this.buildOwnerResource();

        assertThrows(NotFoundException.class,
            () -> resource.getOwnerContentAccess("test_owner_key"));
    }

    @Test
    void usesOwnersCAModeWhenAvailable() {
        String ownerKey = "test-owner-key";
        String expectedMode = "owner-ca-mode";
        List<String> expectedModeList = Collections.singletonList(expectedMode);
        OwnerContentAccess access = new OwnerContentAccess(expectedMode, expectedMode);
        when(mockOwnerCurator.getOwnerContentAccess(eq(ownerKey)))
             .thenReturn(access);

        OwnerResource resource = this.buildOwnerResource();
        ContentAccessDTO contentAccess = resource.getOwnerContentAccess(ownerKey);

        assertEquals(expectedMode, contentAccess.getContentAccessMode());
        assertEquals(expectedModeList, contentAccess.getContentAccessModeList());
    }

    @Test
    void usesDefaultWhenOwnerCANotAvailable() {
        String expectedMode = ContentAccessManager.ContentAccessMode.getDefault().toDatabaseValue();
        List<String> expectedModeList = Arrays.asList(ContentAccessMode.ENTITLEMENT.toDatabaseValue(),
            ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        when(mockOwnerCurator.getOwnerContentAccess(anyString()))
            .thenReturn(new OwnerContentAccess(null, null));

        OwnerResource resource = this.buildOwnerResource();
        ContentAccessDTO contentAccess = resource.getOwnerContentAccess("test_owner_key");

        assertEquals(expectedMode, contentAccess.getContentAccessMode());
        assertEquals(expectedModeList, contentAccess.getContentAccessModeList());
    }


    @Test
    void returns404WhenOwnerNotFound() {
        when(mockOwnerCurator.getOwnerContentAccess(anyString()))
            .thenThrow(OwnerNotFoundException.class);

        OwnerResource resource = this.buildOwnerResource();

        assertThrows(NotFoundException.class,
            () -> resource.getOwnerContentAccess("test_owner"));
    }
}
