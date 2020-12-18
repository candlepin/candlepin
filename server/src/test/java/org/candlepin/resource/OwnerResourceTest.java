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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerContentAccess;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.ProductManager;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.AttributeDTO;
import org.candlepin.dto.api.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.v1.ContentAccessDTO;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.ImportRecordDTO;
import org.candlepin.dto.api.v1.KeyValueParamDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductContentDTO;
import org.candlepin.dto.api.v1.ReleaseVerDTO;
import org.candlepin.dto.api.v1.SystemPurposeAttributesDTO;
import org.candlepin.dto.api.v1.UeberCertificateDTO;
import org.candlepin.dto.api.v1.UpstreamConsumerDTOArrayElement;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.OwnerNotFoundException;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Role;
import org.candlepin.model.SystemPurposeAttributeType;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.ResolverUtil;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.impl.DefaultOwnerServiceAdapter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedMap;


/**
 * OwnerResourceTest
 */
public class OwnerResourceTest extends DatabaseTestFixture {
    private static final String OWNER_NAME = "Jar Jar Binks";

    @Inject private CandlepinPoolManager poolManager;
    @Inject private ServiceLevelValidator serviceLevelValidator;
    @Inject private I18n i18n;
    @Inject private OwnerResource ownerResource;
    @Inject private CalculatedAttributesUtil calculatedAttributesUtil;
    @Inject private Configuration config;
    @Inject private ContentOverrideValidator contentOverrideValidator;
    @Inject private UeberCertificateGenerator ueberCertGenerator;
    @Inject private UeberCertificateCurator ueberCertCurator;
    @Inject private DTOValidator dtoValidator;

    // Mocks used to build the owner resource
    private ActivationKeyCurator mockActivationKeyCurator;
    private ConsumerCurator mockConsumerCurator;
    private ConsumerTypeCurator mockConsumerTypeCurator;
    private ContentAccessManager mockContentAccessManager;
    private ContentManager mockContentManager;
    private EntitlementCurator mockEntitlementCurator;
    private EnvironmentCurator mockEnvironmentCurator;
    private EventSink mockEventSink;
    private EventFactory mockEventFactory;
    private EventAdapter mockEventAdapter;
    private ExporterMetadataCurator mockExportCurator;
    private JobManager mockJobManager;
    private ImportRecordCurator mockImportRecordCurator;
    private ManifestManager mockManifestManager;
    private OwnerCurator mockOwnerCurator;
    private OwnerManager mockOwnerManager;
    private OwnerInfoCurator mockOwnerInfoCurator;
    private OwnerProductCurator mockOwnerProductCurator;
    private PoolManager mockPoolManager;
    private ProductCertificateCurator mockProductCertCurator;
    private ProductCurator mockProductCurator;
    private ProductManager productManager;
    private PrincipalProvider principalProvider;
    private UeberCertificateCurator mockUeberCertCurator;
    private UeberCertificateGenerator mockUeberCertificateGenerator;

    private ContentAccessManager contentAccessManager;
    private UniqueIdGenerator mockIdGenerator;

    private ResolverUtil resolverUtil;
    private OwnerServiceAdapter ownerServiceAdapter;
    private ConsumerTypeValidator consumerTypeValidator;

    private Owner owner;
    private List<Owner> owners;
    private Product product;
    private Set<String> typeLabels;
    private List<String> skus;
    private List<String> subscriptionIds;
    private List<String> contracts;

    @BeforeEach
    public void setUp() {
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

        this.contentAccessManager = mock(ContentAccessManager.class);
        this.mockContentAccessManager = mock(ContentAccessManager.class);
        this.mockContentManager = mock(ContentManager.class);
        this.mockManifestManager = mock(ManifestManager.class);
        this.mockOwnerManager = mock(OwnerManager.class);
        this.mockProductCertCurator = mock(ProductCertificateCurator.class);
        this.mockPoolManager = mock(PoolManager.class);
        this.productManager = mock(ProductManager.class);
        this.mockJobManager = mock(JobManager.class);
        this.mockIdGenerator = mock(UniqueIdGenerator.class);
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
            this.mockEventAdapter, this.contentAccessManager, this.mockManifestManager,
            this.mockPoolManager, this.mockOwnerManager, this.mockExportCurator, this.mockOwnerInfoCurator,
            this.mockImportRecordCurator, this.mockEntitlementCurator, this.mockUeberCertCurator,
            this.mockUeberCertificateGenerator, this.mockEnvironmentCurator, this.calculatedAttributesUtil,
            this.contentOverrideValidator, this.serviceLevelValidator, this.ownerServiceAdapter, this.config,
            this.resolverUtil, this.consumerTypeValidator, this.mockOwnerProductCurator, this.modelTranslator,
            this.mockJobManager, this.dtoValidator, this.ownerContentCurator, this.mockIdGenerator,
            this.mockContentManager, this.productManager, this.mockProductCertCurator,
            this.mockProductCurator, this.principalProvider);
    }

    // TODO: This test does not belong here; it does not hit the resource at all
    private org.candlepin.dto.api.v1.ProductDTO buildTestProductDTO() {
        org.candlepin.dto.api.v1.ProductDTO dto = TestUtil.createProductDTO("test_product");
        dto.getAttributes().add(createAttribute(Product.Attributes.VERSION, "1.0"));
        dto.getAttributes().add(createAttribute(Product.Attributes.VARIANT, "server"));
        dto.getAttributes().add(createAttribute(Product.Attributes.TYPE, "SVC"));
        dto.getAttributes().add(createAttribute(Product.Attributes.ARCHITECTURE, "ALL"));

        return dto;
    }

    private AttributeDTO createAttribute(String name, String value) {
        return new AttributeDTO().name(name).value(value);
    }

    private void addContent(org.candlepin.dto.api.v1.ProductDTO product, ContentDTO dto) {
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
        ownerResource.deleteOwner(owner.getKey(), new Boolean(true), new Boolean(true));
        owner = ownerCurator.get(id);
        assertNull(owner);
    }

    // TODO: This test does not belong here; it does not hit the resource at all
    @Test
    public void testRefreshPoolsWithNewSubscriptions() {
        Product prod = this.createProduct(owner);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto =
            this.modelTranslator.translate(owner, org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();
        List<Pool> pools = poolCurator.listByOwnerAndProduct(owner, prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);

        assertEquals(sub.getId(), newPool.getSubscriptionId());
        assertEquals(sub.getQuantity(), newPool.getQuantity());
        assertEquals(sub.getStartDate(), newPool.getStartDate());
        assertEquals(sub.getEndDate(), newPool.getEndDate());
    }

    // TODO: This test does not belong here; it does not hit the resource at all
    @Test
    public void testRefreshPoolsWithChangedSubscriptions() {
        Product prod = this.createProduct(owner);
        Pool pool = createPool(owner, prod, 1000L,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto = this.modelTranslator.translate(owner,
            org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        assertTrue(pool.getQuantity() < sub.getQuantity());
        assertTrue(pool.getStartDate() != sub.getStartDate());
        assertTrue(pool.getEndDate() != sub.getEndDate());

        pool.getSourceSubscription().setSubscriptionId(sub.getId());
        poolCurator.merge(pool);

        poolManager.getRefresher(subAdapter).add(owner).run();

        pool = poolCurator.get(pool.getId());
        assertEquals(sub.getId(), pool.getSubscriptionId());
        assertEquals(sub.getQuantity(), pool.getQuantity());
        assertEquals(sub.getStartDate(), pool.getStartDate());
        assertEquals(sub.getEndDate(), pool.getEndDate());
    }

    // TODO: This test does not belong here; it does not hit the resource at all
    @Test
    public void testRefreshPoolsWithRemovedSubscriptions() {
        Product prod = this.createProduct(owner);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto =
            this.modelTranslator.translate(owner, org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));

        // This line is only present as a result of a (temporary?) fix for BZ 1452694. Once a
        // better fix has been implemented, the upstream pool ID can be removed.
        sub.setUpstreamPoolId("upstream_pool_id");

        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.listByOwnerAndProduct(owner, prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);
        String poolId = newPool.getId();

        // Now delete the subscription:
        subscriptions.remove(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();
        assertNull(poolCurator.get(poolId));
    }

    // TODO: This test does not belong here; it does not hit the resource at all
    @Test
    public void testRefreshMultiplePools() {
        Product prod = this.createProduct(owner);
        Product prod2 = this.createProduct(owner);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto =
            this.modelTranslator.translate(owner, org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        SubscriptionDTO sub2 = new SubscriptionDTO();
        sub2.setId(Util.generateDbUUID());
        sub2.setOwner(ownerDto);
        sub2.setProduct(this.modelTranslator.translate(prod2, ProductDTO.class));
        sub2.setQuantity(800L);
        sub2.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub2.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub2.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub2);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(2, pools.size());
    }

    // TODO: This test does not belong here; it does not hit the resource at all
    // test covers scenario from bug 1012386
    @Test
    public void testRefreshPoolsWithRemovedMasterPool() {
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        createProduct(prod, owner);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto =
            this.modelTranslator.translate(owner, org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.getBySubscriptionId(owner, sub.getId());
        assertEquals(2, pools.size());
        String bonusId =  "";
        String masterId = "";

        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("master")) {
                poolCurator.delete(p);
                masterId = p.getId();
            }
            else {
                bonusId = p.getId();
            }
        }

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        assertNull(poolCurator.get(masterId), "Original Master Pool should be gone");
        assertNotNull(poolCurator.get(bonusId), "Bonus Pool should be the same");
        // master pool should have been recreated
        pools = poolCurator.getBySubscriptionId(owner, sub.getId());
        assertEquals(2, pools.size());
        boolean newMaster = false;
        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("master")) {
                newMaster = true;
            }
        }
        assertTrue(newMaster);
    }

    // TODO: This test does not belong here; it does not hit the resource at all
    // test covers a corollary scenario from bug 1012386
    @Test
    public void testRefreshPoolsWithRemovedBonusPool() {
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        createProduct(prod, owner);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto =
            this.modelTranslator.translate(owner, org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.getBySubscriptionId(owner, sub.getId());
        assertEquals(2, pools.size());
        String bonusId =  "";
        String masterId = "";

        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("derived")) {
                poolCurator.delete(p);
                bonusId = p.getId();
            }
            else {
                masterId = p.getId();
            }
        }

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        assertNull(poolCurator.get(bonusId), "Original bonus pool should be gone");
        assertNotNull(poolCurator.get(masterId), "Master pool should be the same");
        // master pool should have been recreated
        pools = poolCurator.getBySubscriptionId(owner, sub.getId());
        assertEquals(2, pools.size());
        boolean newBonus = false;
        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("derived")) {
                newBonus = true;
            }
        }
        assertTrue(newBonus);
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

        ownerResource.deleteOwner(owner.getKey(), new Boolean(true),  new Boolean(true));

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

        ownerResource.listPools(owner.getKey(), null, null, null, null, false, null,
            null, new ArrayList<>(), false, false, null, null);
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
        List<PoolDTO> nowList = ownerResource.listPools(owner.getKey(), c.getUuid(), null, null, null, false,
            Util.toDateTime(new Date()), null, new ArrayList<>(), false, false, null, null);

        assertEquals(1, nowList.size());
        assert (nowList.get(0).getId().equals(pool1.getId()));

        Date activeOn = new Date(pool2.getStartDate().getTime() + 1000L * 60 * 60 * 24);
        List<PoolDTO> futureList = ownerResource.listPools(owner.getKey(), c.getUuid(), null, null, null,
            false, Util.toDateTime(activeOn), null, new ArrayList<>(), false, false, null, null);
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

        List<PoolDTO> pools = ownerResource.listPools(owner.getKey(),
            null, null, null, null, true, null, null, new ArrayList<>(), false, false, null, null);
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

        List<KeyValueParamDTO> params = new ArrayList<>();
        params.add(createKeyValueParam("cores", "12"));

        when(this.principalProvider.get()).thenReturn(principal);
        List<PoolDTO> pools = ownerResource.listPools(owner.getKey(), null,
            null, null, null, true, null, null, params, false, false, null, null);
        assertEquals(1, pools.size());
        assertModelEqualsDTO(pool2, pools.get(0));

        params.clear();
        params.add(createKeyValueParam("virt_only", "true"));

        pools = ownerResource.listPools(owner.getKey(), null, null,
            null, null, true, null, null, params, false, false, null, null);
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

        List<KeyValueParamDTO> params = new ArrayList<>();

        when(this.principalProvider.get()).thenReturn(principal);
        List<PoolDTO> pools = ownerResource.listPools(owner.getKey(), null,
            null, null, null, true, null, null, params, false, false, null, null);
        assertEquals(2, pools.size());

        params = new ArrayList<>();
        params.add(createKeyValueParam(Pool.Attributes.DEVELOPMENT_POOL, "!true"));
        pools = ownerResource.listPools(owner.getKey(), null,
            null, null, null, true, null, null, params, false, false, null, null);
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
            ownerResource.listPools(owner.getKey(), null, null, null, null, true, null,
            null, new ArrayList<>(), false, false, null, null));
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
        assertThrows(ForbiddenException.class, () ->
            ownerResource.deleteOwner(owner.getKey(), true, false)
        );
    }

    @Test
    public void consumerCannotListAllConsumersInOwner() {
        Consumer c = createConsumer(owner);
        Principal principal = setupPrincipal(new ConsumerPrincipal(c, owner));
        when(this.principalProvider.get()).thenReturn(principal);

        securityInterceptor.enable();
        assertThrows(ForbiddenException.class, () ->
            ownerResource.listConsumers(owner.getKey(), null, null, new ArrayList<>(), null, null, null,
            null, null)
        );
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

        CandlepinQuery<ConsumerDTOArrayElement> result = ownerResource.listConsumers(
            owner.getKey(), "username", types, uuids, null, null, null, null, null);

        assertNotNull(result);
        List<ConsumerDTOArrayElement> consumers = result.list();

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

        CandlepinQuery<ConsumerDTOArrayElement> result = ownerResource.listConsumers(
            owner.getKey(), null, null, uuids, null, null, null, null, null);

        assertNotNull(result);
        List<ConsumerDTOArrayElement> consumers = result.list();

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

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            ownerResource.listConsumers(owner.getKey(), null, types, new ArrayList<>(), null,
            null, null, null, null)
        );
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

        CandlepinQuery<ConsumerDTOArrayElement> result = ownerResource.listConsumers(
            owner.getKey(), null, null, uuids, null, null, null, null, null);

        assertNotNull(result);
        List<ConsumerDTOArrayElement> consumers = result.list();

        assertEquals(2, consumers.size());
    }

    //copied from consumerCannotListAllConsumersInOwner
    @Test
    public void consumerCannotCountAllConsumersInOwner() {
        Consumer c = createConsumer(owner);
        Principal principal = setupPrincipal(new ConsumerPrincipal(c, owner));
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        assertThrows(ForbiddenException.class, () ->
            ownerResource.countConsumers(owner.getKey(), typeLabels, skus, subscriptionIds, contracts)
        );
    }

    //copied from failWhenListingByBadConsumerType
    @Test
    public void failWhenCountingByBadConsumerType() {
        Set<String> types = new HashSet<>();
        types.add("unknown");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            ownerResource.countConsumers(owner.getKey(), types, skus, subscriptionIds, contracts)
        );
        assertEquals("No such unit type(s): unknown", ex.getMessage());
    }

    @Test
    public void countShouldThrowExceptionIfUnknownOwner() {
        String key = "unknown";
        createConsumer(owner);

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
            ownerResource.countConsumers(key, typeLabels, skus, subscriptionIds, contracts)
        );
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

        List<PoolDTO> pools = ownerResource.listPools(owner.getKey(), c.getUuid(), null,
            p.getId(), null, true, null, null, new ArrayList<>(), false, false, null, null);
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

        assertThrows(NotFoundException.class, () -> ownerResource.listPools(
            owner.getKey(), c.getUuid(), null, p.getUuid(),  null, true, null, null,
            new ArrayList<>(), false, false, null, null)
        );
    }

    @Test
    public void testEntitlementsRevocationWithLifoOrder() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(7, 4, 5);
        assertEquals(5L, this.poolCurator.get(pool.getId()).getConsumed().longValue());
    }

    @Test
    public void testEntitlementsRevocationWithNoOverflow() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(10, 4, 5);
        assertEquals(9L, this.poolCurator.get(pool.getId()).getConsumed().longValue());
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

        when(oc.getByKey(anyString())).thenReturn(o);
        ActivationKeyDTO key = new ActivationKeyDTO();
        assertThrows(BadRequestException.class, () -> resource.createActivationKey(owner.getKey(), key));
    }

    @Test
    public void testActivationKeyTooLongRelease() {
        OwnerCurator oc = mock(OwnerCurator.class);
        Owner o = new Owner();
        o.setKey("owner-key");

        when(this.mockOwnerCurator.getByKey(anyString())).thenReturn(o);
        OwnerResource resource = this.buildOwnerResource();

        when(oc.getByKey(anyString())).thenReturn(o);
        ActivationKeyDTO key = new ActivationKeyDTO();
        key.releaseVer(new ReleaseVerDTO().releaseVer(TestUtil.getStringOfSize(256)));

        assertThrows(BadRequestException.class, () ->
            resource.createActivationKey(owner.getKey(), key)
        );
    }

    @Test
    public void testValidationCreateWithNullProductId() {
        NestedOwnerDTO nestedOwnerDTO = new NestedOwnerDTO();
        nestedOwnerDTO.key(owner.getKey());

        ActivationKeyDTO key = new ActivationKeyDTO()
            .owner(nestedOwnerDTO)
            .name("dd");

        Set<ActivationKeyProductDTO> products = new HashSet<>();
        products.add(new ActivationKeyProductDTO().productId(null));
        key.products(products);

        assertThrows(javax.validation.ConstraintViolationException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
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

        assertThrows(javax.validation.ConstraintViolationException.class, () ->
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

        assertThrows(IllegalArgumentException.class, () ->
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

        assertThrows(javax.validation.ConstraintViolationException.class, () ->
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

        assertThrows(javax.validation.ConstraintViolationException.class, () ->
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

        assertThrows(IllegalArgumentException.class, () ->
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

        assertThrows(javax.validation.ConstraintViolationException.class, () ->
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

        assertThrows(javax.validation.ConstraintViolationException.class, () ->
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

        assertThrows(javax.validation.ConstraintViolationException.class, () ->
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

        assertThrows(javax.validation.ConstraintViolationException.class, () ->
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

        assertThrows(IllegalArgumentException.class, () ->
            this.ownerResource.createActivationKey(owner.getKey(), key)
        );
    }

    private Pool doTestEntitlementsRevocationCommon(long subQ, int e1, int e2) throws ParseException {
        Product prod = this.createProduct(owner);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto =
            this.modelTranslator.translate(owner, org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(1000L);
        sub.setStartDate(TestUtil.createDate(2009, 11, 30));
        sub.setEndDate(TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 10, 10, 30));
        sub.setLastModified(TestUtil.createDate(2015, 11, 30));
        subscriptions.add(sub);

        List<Pool> pools = poolManager.createAndEnrichPools(sub);
        assertTrue(pools.size() > 0);
        Pool pool = pools.get(0);

        sub.setQuantity(subQ);

        Owner retrieved = pool.getOwner();
        Consumer consumer = createConsumer(retrieved);
        Consumer consumer1 = createConsumer(retrieved);

        pool = this.poolCurator.get(pool.getId());
        createEntitlementWithQ(pool, retrieved, consumer, e1, "01/02/2010");
        createEntitlementWithQ(pool, retrieved, consumer1, e2, "01/01/2010");
        assertEquals(pool.getConsumed(), Long.valueOf(e1 + e2));

        poolManager.getRefresher(subAdapter).add(retrieved).run();
        pool = poolCurator.get(pool.getId());
        return pool;
    }

    /**
     * @param pool
     * @param owner
     * @param consumer
     * @return
     */
    private Entitlement createEntitlementWithQ(Pool pool, Owner owner,
        Consumer consumer, int quantity, String date) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        Entitlement e1 = createEntitlement(owner, consumer, pool, null);
        e1.setQuantity(quantity);
        pool.getEntitlements().add(e1);

        this.entitlementCurator.create(e1);
        e1.getPool().setConsumed(e1.getPool().getConsumed() + quantity);
        this.poolCurator.merge(e1.getPool());

        e1.setCreated(dateFormat.parse(date));
        this.entitlementCurator.merge(e1);

        return e1;
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
        ownerResource.deleteOwner(owner.getKey(), new Boolean(false),  new Boolean(false));
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
            new Boolean(true), new Boolean(true)));
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

        Product prod1 = TestUtil.createProduct();
        prod1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "premium");
        createProduct(prod1, owner);
        Product prod2 = TestUtil.createProduct();
        prod2.setAttribute(Product.Attributes.SUPPORT_LEVEL, "standard");
        createProduct(prod2, owner);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto =
            this.modelTranslator.translate(owner, org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub1 = new SubscriptionDTO();
        sub1.setId(Util.generateDbUUID());
        sub1.setOwner(ownerDto);
        sub1.setProduct(this.modelTranslator.translate(prod1, ProductDTO.class));
        sub1.setQuantity(2000L);
        sub1.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub1.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub1.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub1);

        SubscriptionDTO sub2 = new SubscriptionDTO();
        sub2.setId(Util.generateDbUUID());
        sub2.setOwner(ownerDto);
        sub2.setProduct(this.modelTranslator.translate(prod2, ProductDTO.class));
        sub2.setQuantity(2000L);
        sub2.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub2.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub2.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub2);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

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
    public void testImportManifestSynchronousSuccess() throws IOException, ImporterException {
        OwnerResource thisOwnerResource = new OwnerResource(
            ownerCurator, null, null, i18n, this.mockEventSink, mockEventFactory, null, null,
            this.mockManifestManager, null, null, null, null, importRecordCurator, null, null, null, null,
            null, contentOverrideValidator, serviceLevelValidator, null, null, null, null, null,
            this.modelTranslator, this.mockJobManager, null, null,
            null, null, null, null , null, null);
        MultipartInput input = mock(MultipartInput.class);
        InputPart part = mock(InputPart.class);
        File archive = mock(File.class);
        List<InputPart> parts = new ArrayList<>();
        parts.add(part);
        MultivaluedMap<String, String> mm = new MultivaluedMapImpl<>();
        List<String> contDis = new ArrayList<>();
        contDis.add("form-data; name=\"upload\"; filename=\"test_file.zip\"");
        mm.put("Content-Disposition", contDis);

        when(input.getParts()).thenReturn(parts);
        when(part.getHeaders()).thenReturn(mm);
        when(part.getBody(any(GenericType.class))).thenReturn(archive);

        ImportRecord ir = new ImportRecord(owner);
        when(this.mockManifestManager.importManifest(eq(owner), any(File.class), eq("test_file.zip"),
            any(ConflictOverrides.class))).thenReturn(ir);

        ImportRecordDTO expected = this.modelTranslator.translate(ir, ImportRecordDTO.class);
        ImportRecordDTO response = thisOwnerResource.importManifest(owner.getKey(), new ArrayList<>(), input);

        assertNotNull(response);
        assertEquals(expected, response);
    }

    @Test
    public void testImportManifestAsyncSuccess() throws IOException, ImporterException, JobException {
        OwnerResource thisOwnerResource = new OwnerResource(
            this.mockOwnerCurator, null, null, i18n, this.mockEventSink, mockEventFactory, null, null,
            this.mockManifestManager, null, null, null, null, importRecordCurator, null, null, null, null,
            null, contentOverrideValidator, serviceLevelValidator, null, null, null, null, null,
            this.modelTranslator, this.mockJobManager, null, null,
            null, null, null, null , null, null);

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
    public void testImportManifestFailure() throws IOException, ImporterException {
        OwnerResource thisOwnerResource = new OwnerResource(
            ownerCurator, null, null, i18n, this.mockEventSink, mockEventFactory,
            null, mockContentAccessManager, this.mockManifestManager, null, null,
            null, null, importRecordCurator, null, null,
            null, null, null, contentOverrideValidator,
            serviceLevelValidator, null, null, null, null,
            null, this.modelTranslator, this.mockJobManager, null, null,
            null, null, null, null , null, null);

        MultipartInput input = mock(MultipartInput.class);
        InputPart part = mock(InputPart.class);
        File archive = mock(File.class);
        List<InputPart> parts = new ArrayList<>();
        parts.add(part);
        MultivaluedMap<String, String> mm = new MultivaluedMapImpl<>();
        List<String> contDis = new ArrayList<>();
        contDis.add("form-data; name=\"upload\"; filename=\"test_file.zip\"");
        mm.put("Content-Disposition", contDis);

        when(input.getParts()).thenReturn(parts);
        when(part.getHeaders()).thenReturn(mm);
        when(part.getBody(any(GenericType.class))).thenReturn(archive);

        ImporterException expectedException = new ImporterException("Bad import");
        when(this.mockManifestManager.importManifest(eq(owner), any(File.class), any(String.class),
            any(ConflictOverrides.class))).thenThrow(expectedException);

        try {
            thisOwnerResource.importManifest(owner.getKey(), new ArrayList<>(), input);
            fail("Expected IseException was not thrown");
        }
        catch (IseException ise) {
            // expected, so we catch and go on.
        }

        verify(this.mockManifestManager).recordImportFailure(eq(owner), eq(expectedException),
            eq("test_file.zip"));
    }

    @Test
    public void upstreamConsumers() {
        Principal p = mock(Principal.class);
        UpstreamConsumer upstream = mock(UpstreamConsumer.class);
        Owner owner = mock(Owner.class);

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

    private KeyValueParamDTO createKeyValueParam(String key, String val) {
        return new KeyValueParamDTO()
            .key(key)
            .value(val);
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
        pool.setSubscriptionSubKey("master");
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
        assertTrue(pools.get(0).getSubscriptionSubKey().startsWith("master") ||
            pools.get(1).getSubscriptionSubKey().startsWith("master"));
        assertTrue(pools.get(0).getSubscriptionSubKey().equals("derived") ||
            pools.get(1).getSubscriptionSubKey().equals("derived"));
    }

    @Test
    public void createBonusPoolForUpdate() {
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        createProduct(prod, owner);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setUpstreamPoolId("upstream-" + pool.getId());
        pool.setSubscriptionSubKey("master");
        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);
        poolDto = ownerResource.createPool(owner.getKey(), poolDto);

        poolDto.setQuantity(100L);
        ownerResource.updatePool(owner.getKey(), poolDto);

        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(2, pools.size());
        assertTrue(pools.get(0).getSubscriptionSubKey().startsWith("master") ||
            pools.get(1).getSubscriptionSubKey().startsWith("master"));
        assertTrue(pools.get(0).getSubscriptionSubKey().equals("derived") ||
            pools.get(1).getSubscriptionSubKey().equals("derived"));
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
        pool.setSubscriptionSubKey("master");
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
        pool.setSubscriptionSubKey("master");
        PoolDTO poolDto = this.modelTranslator.translate(pool, PoolDTO.class);
        ownerResource.createPool(owner.getKey(), poolDto);
        List<Pool> pools = poolCurator.listByOwner(owner).list();

        Pool bonusPool = null;
        for (Pool p : pools) {
            if (p.getSubscriptionSubKey().contentEquals("derived")) {
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
            .ownerEntitlements(owner.getKey(), null, null, null);

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
            resource.ownerEntitlements("Taylor Swift", null, null, null)
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

        CandlepinQuery mockQuery = mock(CandlepinQuery.class);
        when(this.mockOwnerProductCurator.getProductsByOwner(eq(owner)))
            .thenReturn(mockQuery);

        Product p1 = TestUtil.createProduct();
        Product p2 = TestUtil.createProduct();
        Product p3 = TestUtil.createProduct();

        p1.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "hello, world");
        p2.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "hello, earth");
        p3.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "earth, world");

        p1.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "production");
        p2.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "production");
        p3.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "development");

        List<Product> dummyProducts = new ArrayList<>(Arrays.asList(p1, p2, p3));
        when(mockQuery.list()).thenReturn(dummyProducts);

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

        when(this.mockConsumerCurator.getDistinctSyspurposeRolesByOwner(eq(owner)))
            .thenReturn(mockRoles);
        when(this.mockConsumerCurator.getDistinctSyspurposeServicelevelByOwner(eq(owner)))
            .thenReturn(mockSLAs);
        when(this.mockConsumerCurator.getDistinctSyspurposeUsageByOwner(eq(owner)))
            .thenReturn(mockUsages);
        when(this.mockConsumerCurator.getDistinctSyspurposeAddonsByOwner(eq(owner)))
            .thenReturn(mockAddons);

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
    }

    @Test
    public void testCreateOwnerSetContentAccessListAndMode() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

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
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode + "," + orgEnvMode);

        OwnerDTO output = this.ownerResource.createOwner(changes);

        assertNotNull(output);

        assertEquals(entitlementMode + "," + orgEnvMode, output.getContentAccessModeList());
        assertEquals(entitlementMode, output.getContentAccessMode());
    }

    @Test
    public void testCreateOwnerSetContentAccessModeCannotUseInvalidModeInList() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode + ",unsupported_mode");

        assertThrows(BadRequestException.class, () -> ownerResource.createOwner(changes));
    }

    @Test
    public void testCreateOwnerSetContentAccessModeCannotUseInvalidMode() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode)
            .contentAccessMode(orgEnvMode);

        assertThrows(BadRequestException.class, () -> ownerResource.createOwner(changes));
    }

    @Test
    public void testCreateOwnerSetContentAccessModeCannotUseImpliedDefault() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(orgEnvMode);

        // This should fail since creation requires that we specify the values, even if those
        // values are defaults. As such, the default value of entitlement should not be allowed
        // at this point.

        assertThrows(BadRequestException.class, () -> ownerResource.createOwner(changes));
    }

    @Test
    public void testCreateOwnerCannotUseNonDefaultContentAccessModeInStandalone() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");

        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(orgEnvMode)
            .contentAccessMode(orgEnvMode);

        assertThrows(BadRequestException.class, () -> ownerResource.createOwner(changes));
    }

    @Test
    public void testCreateOwnerCanSetNonDefaultContentAccessModeListInStandalone() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        OwnerDTO changes = new OwnerDTO()
            .key("test_owner")
            .displayName("test_owner")
            .contentAccessModeList(entitlementMode + "," + orgEnvMode);

        OwnerDTO output = this.ownerResource.createOwner(changes);

        assertNotNull(output);

        assertEquals(entitlementMode + "," + orgEnvMode, output.getContentAccessModeList());
        assertEquals(entitlementMode, output.getContentAccessMode());
    }

    @Test
    public void testSetContentAccessModeList() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

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
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

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
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

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
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessModeList(entitlementMode + ",unsupported_mode");

        assertThrows(BadRequestException.class, () -> ownerResource.updateOwner(owner.getKey(), changes));
    }

    @Test
    public void testSetContentAccessModeCannotUseInvalidMode() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessMode(orgEnvMode);

        assertThrows(BadRequestException.class, () -> ownerResource.updateOwner(owner.getKey(), changes));
    }

    @Test
    public void testUpdateCannotChangeContentAccessModeInStandalone() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessMode(orgEnvMode);

        assertThrows(BadRequestException.class, () -> ownerResource.updateOwner(owner.getKey(), changes));
    }

    @Test
    public void testUpdateCannotChangeContentAccessModeListInStandalone() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");

        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        Owner owner = this.ownerCurator.create(new Owner("test_owner", "test_owner")
            .setContentAccessModeList(entitlementMode)
            .setContentAccessMode(entitlementMode));

        OwnerDTO changes = new OwnerDTO()
            .contentAccessModeList(orgEnvMode);

        assertThrows(BadRequestException.class, () -> ownerResource.updateOwner(owner.getKey(), changes));
    }

    @Test
    void shouldThrowWhenOwnerNotFound() {
        when(mockOwnerCurator.getOwnerContentAccess(anyString()))
            .thenThrow(OwnerNotFoundException.class);

        OwnerResource resource = this.buildOwnerResource();

        Assertions.assertThrows(NotFoundException.class,
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
        List<String> expectedModeList = Collections.singletonList(expectedMode);
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

        Assertions.assertThrows(NotFoundException.class,
            () -> resource.getOwnerContentAccess("test_owner"));
    }

    //
    // Tests related to Owner Content
    //

    @Test
    public void listOwnerContent() throws Exception {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = this.modelTranslator.translate(content, ContentDTO.class);

        CandlepinQuery<ContentDTO> response = this.ownerResource.listOwnerContent(owner.getKey());

        assertNotNull(response);

        Collection<ContentDTO> received = response.list();

        assertEquals(1, received.size());
        assertTrue(received.contains(cdto));
    }

    @Test
    public void listOwnerContentNoContent() throws Exception {
        Owner owner = this.createOwner("test_owner");
        CandlepinQuery<ContentDTO> response = this.ownerResource.listOwnerContent(owner.getKey());

        assertNotNull(response);

        Collection<ContentDTO> received = response.list();

        assertEquals(0, received.size());
    }

    @Test
    public void getOwnerContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO output = this.ownerResource.getOwnerContent(owner.getKey(), content.getId());

        assertNotNull(output);
        assertEquals(content.getId(), output.getId());
    }

    @Test
    public void getOwnerContentNotFound() {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class, () ->
            this.ownerResource.getOwnerContent(owner.getKey(), "test_content")
        );
    }

    @Test
    public void createContent() {
        Owner owner = this.createOwner("test_owner");
        ContentDTO cdto = TestUtil.createContentDTO("test_content");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        assertNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        ContentDTO output = this.ownerResource.createContent(owner.getKey(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());

        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());
        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
        assertEquals(cdto.getLabel(), entity.getLabel());
        assertEquals(cdto.getType(), entity.getType());
        assertEquals(cdto.getVendor(), entity.getVendor());
    }

    @Test
    public void createContentWhenContentAlreadyExists()  {
        // Note:
        // The current behavior of createContent is to update content if content already exists
        // with the given RHID. So, our expected behavior in this test is to trigger an update.

        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        assertNotNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        ContentDTO output = this.ownerResource.createContent(owner.getKey(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());
        assertEquals(cdto.getName(), output.getName());

        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());
        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
    }

    @Test
    public void createContentWhenContentAlreadyExistsAndLocked()  {
        // Note:
        // The current behavior of createContent is to update content if content already exists
        // with the given RHID. So, our expected behavior in this test is to trigger an update.

        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        content.setLocked(true);
        this.contentCurator.merge(content);

        assertNotNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerResource.createContent(owner.getKey(), cdto)
        );
        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());
        assertNotNull(entity);
        assertEquals(content, entity);
        assertNotEquals(cdto.getName(), entity.getName());

    }

    @Test
    public void updateContent()  {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");

        assertNotNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        ContentDTO output = this.ownerResource.updateContent(owner.getKey(), cdto.getId(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());
        assertEquals(cdto.getName(), output.getName());

        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());

        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
    }

    @Test
    public void updateContentThatDoesntExist()  {
        Owner owner = this.createOwner("test_owner");
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");

        assertNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        assertThrows(NotFoundException.class, () ->
            this.ownerResource.updateContent(owner.getKey(), cdto.getId(), cdto)
        );
        assertNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));
    }

    @Test
    public void updateLockedContent()  {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");
        content.setLocked(true);
        this.contentCurator.merge(content);

        assertNotNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerResource.updateContent(owner.getKey(), cdto.getId(), cdto)
        );
        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());
        assertNotNull(entity);
        assertEquals(content, entity);
        assertNotEquals(cdto.getName(), entity.getName());
    }

    @Test
    public void deleteContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        Environment environment = this.createEnvironment(owner, "test_env", "test_env", null, null,
            Arrays.asList(content));

        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        this.ownerResource.remove(owner.getKey(), content.getId());

        assertNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        this.environmentCurator.evict(environment);
        environment = this.environmentCurator.get(environment.getId());

        assertEquals(0, environment.getEnvironmentContent().size());
    }

    @Test
    public void deleteLockedContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        content.setLocked(true);
        this.contentCurator.merge(content);

        Environment environment = this.createEnvironment(owner, "test_env", "test_env", null,
            null, Arrays.asList(content));

        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerResource.remove(owner.getKey(), content.getId())
        );
        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        this.environmentCurator.evict(environment);
        environment = this.environmentCurator.get(environment.getId());

        assertEquals(1, environment.getEnvironmentContent().size());
    }

    @Test
    public void deleteContentWithNonExistentContent() {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class, () ->
            this.ownerResource.remove(owner.getKey(), "test_content")
        );
    }

    @Test
    public void testUpdateContentThrowsExceptionWhenOwnerDoesNotExist() {
        ContentDTO cdto = TestUtil.createContentDTO("test_content");

        assertThrows(NotFoundException.class, () ->
            this.ownerResource.updateContent("fake_owner_key", cdto.getId(), cdto)
        );
    }

    //
    // Tests related to Owner Product
    //

    @Test
    public void testCreateProductResource() {
        Owner owner = this.createOwner("Example-Corporation");
        org.candlepin.dto.api.v1.ProductDTO pdto = this.buildTestProductDTO();

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), pdto.getId()));

        org.candlepin.dto.api.v1.ProductDTO result = this.ownerResource
            .createProductByOwner(owner.getKey(), pdto);
        Product entity = this.ownerProductCurator.getProductById(owner, pdto.getId());
        org.candlepin.dto.api.v1.ProductDTO expected = this.modelTranslator.translate(entity,
            org.candlepin.dto.api.v1.ProductDTO.class);

        assertNotNull(result);
        assertNotNull(entity);
        assertEquals(expected, result);
    }

    @Test
    public void testCreateProductWithContent() {
        Owner owner = this.createOwner("Example-Corporation");
        Content content = this.createContent("content-1", "content-1", owner);
        org.candlepin.dto.api.v1.ProductDTO product = this.buildTestProductDTO();
        ContentDTO contentDTO = this.modelTranslator.translate(content, ContentDTO.class);
        addContent(product, contentDTO);

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), product.getId()));

        org.candlepin.dto.api.v1.ProductDTO result = this.ownerResource
            .createProductByOwner(owner.getKey(), product);
        Product entity = this.ownerProductCurator.getProductById(owner, product.getId());
        org.candlepin.dto.api.v1.ProductDTO expected = this.modelTranslator.translate(entity,
            org.candlepin.dto.api.v1.ProductDTO.class);

        assertNotNull(result);
        assertNotNull(entity);
        assertEquals(expected, result);

        assertNotNull(result.getProductContent());
        assertEquals(1, result.getProductContent().size());
        assertEquals(contentDTO, result.getProductContent().iterator().next().getContent());
    }

    @Test
    public void testUpdateProductWithoutId() {
        Owner owner = this.createOwner("Update-Product-Owner");
        org.candlepin.dto.api.v1.ProductDTO pdto = this.buildTestProductDTO();

        org.candlepin.dto.api.v1.ProductDTO product = this.ownerResource
            .createProductByOwner(owner.getKey(), pdto);
        org.candlepin.dto.api.v1.ProductDTO update = TestUtil.createProductDTO(product.getId());
        update.setName(product.getName());
        update.getAttributes().add(createAttribute("attri", "bute"));
        org.candlepin.dto.api.v1.ProductDTO result = this.ownerResource
            .updateProductByOwner(owner.getKey(), product.getId(), update);
        assertEquals("bute", result.getAttributes().get(0).getValue());
    }

    @Test
    public void testUpdateProductIdMismatch() {
        Owner owner = this.createOwner("Update-Product-Owner");
        org.candlepin.dto.api.v1.ProductDTO pdto = this.buildTestProductDTO();
        org.candlepin.dto.api.v1.ProductDTO product = this.ownerResource
            .createProductByOwner(owner.getKey(), pdto);
        org.candlepin.dto.api.v1.ProductDTO update = this.buildTestProductDTO();
        update.setId("TaylorSwift");

        assertThrows(BadRequestException.class, () ->
            this.ownerResource.updateProductByOwner(owner.getKey(), product.getId(), update)
        );
    }

    @Test
    public void testDeleteProductWithSubscriptions() {
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        OwnerResource resource = new OwnerResource(
            this.mockOwnerCurator, null, null, i18n, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, this.mockOwnerProductCurator, this.modelTranslator, this.mockJobManager,
            this.dtoValidator, this.ownerContentCurator, null, null, this.productManager,
            this.mockProductCertCurator, this.mockProductCurator, null);

        Owner o = mock(Owner.class);
        Product p = mock(Product.class);

        when(this.mockOwnerCurator.getByKey(eq("owner"))).thenReturn(o);
        when(this.mockOwnerProductCurator.getProductById(eq(o), eq("10"))).thenReturn(p);

        Set<Subscription> subs = new HashSet<>();
        Subscription s = mock(Subscription.class);
        subs.add(s);
        when(this.mockProductCurator.productHasSubscriptions(eq(o), eq(p))).thenReturn(true);

        assertThrows(BadRequestException.class, () -> resource.deleteProductByOwner("owner", "10"));
    }

    @Test
    public void testUpdateLockedProductFails() {
        Owner owner = this.createOwner("test_owner");
        Product product = this.createProduct("test_product", "test_product", owner);
        org.candlepin.dto.api.v1.ProductDTO pdto = TestUtil.createProductDTO("test_product", "updated_name");
        product.setLocked(true);
        this.productCurator.merge(product);

        assertNotNull(this.ownerProductCurator.getProductById(owner, pdto.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerResource.updateProductByOwner(owner.getKey(), pdto.getId(), pdto)
        );
        Product entity = this.ownerProductCurator.getProductById(owner, pdto.getId());
        org.candlepin.dto.api.v1.ProductDTO expected = this.modelTranslator.translate(entity,
            org.candlepin.dto.api.v1.ProductDTO.class);

        assertNotNull(entity);
        assertNotEquals(expected, pdto);
    }

    @Test
    public void testDeleteLockedProductFails() {
        Owner owner = this.createOwner("test_owner");
        Product product = this.createProduct("test_product", "test_product", owner);
        product.setLocked(true);
        this.productCurator.merge(product);

        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerResource.deleteProductByOwner(owner.getKey(), product.getId())
        );
        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product", owner);

        securityInterceptor.enable();
        org.candlepin.dto.api.v1.ProductDTO result = this.ownerResource.getProductByOwner(owner.getKey(),
            entity.getId());
        org.candlepin.dto.api.v1.ProductDTO expected = this.modelTranslator.translate(entity,
            org.candlepin.dto.api.v1.ProductDTO.class);

        assertNotNull(result);
        assertEquals(expected, result);
    }

    @Test
    public void getProductCertificate() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct("123", "AwesomeOS Core", owner);
        // ensure we check SecurityHole
        securityInterceptor.enable();

        ProductCertificate cert = new ProductCertificate();
        cert.setCert("some text");
        cert.setKey("some key");
        cert.setProduct(entity);
        productCertificateCurator.create(cert);

        ProductCertificateDTO cert1 = ownerResource.getProductCertificateByOwner(owner.getKey(),
            entity.getId());
        ProductCertificateDTO expected = this.modelTranslator.translate(cert, ProductCertificateDTO.class);
        assertEquals(cert1, expected);
    }

    @Test
    public void requiresNumericIdForProductCertificates() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct("MCT123", "AwesomeOS", owner);
        securityInterceptor.enable();

        assertThrows(BadRequestException.class, () ->
            ownerResource.getProductCertificateByOwner(owner.getKey(), entity.getId())
        );
    }
}
