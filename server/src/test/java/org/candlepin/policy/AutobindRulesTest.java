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
package org.candlepin.policy;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;

import com.google.inject.Provider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AutobindRulesTest
 */
public class AutobindRulesTest {
    @Mock private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock private JsRunnerRequestCache cache;
    @Mock private Configuration config;
    @Mock private RulesCurator rulesCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private ProductCurator mockProductCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private EnvironmentCurator environmentCurator;

    private ComplianceStatus compliance;
    private AutobindRules autobindRules; // TODO rename
    private Owner owner;
    private Consumer consumer;
    private String productId = "a-product";
    private ModelTranslator translator;
    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = LoggerFactory.getLogger(AutobindRules.class);

    private static final String HIGHEST_QUANTITY_PRODUCT = "QUANTITY001";
    private Map<String, String> activeGuestAttrs;

    @Before
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(TestDateUtil.date(2010, 1, 1));
        when(cacheProvider.get()).thenReturn(cache);
        jsRules = new JsRunnerProvider(rulesCurator, cacheProvider).get();
        mapper =  new RulesObjectMapper(new ProductCachedSerializationModule(mockProductCurator));

        translator = new StandardTranslator(consumerTypeCurator, environmentCurator, mockOwnerCurator);
        autobindRules = new AutobindRules(jsRules, mockProductCurator, consumerTypeCurator, mockOwnerCurator,
           mapper, translator);

        owner = new Owner();
        owner.setId(TestUtil.randomString());
        when(mockOwnerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        consumer = new Consumer("test consumer", "test user", owner, ctype);

        when(consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()))).thenReturn(ctype);
        when(consumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

        compliance = new ComplianceStatus();
        activeGuestAttrs = new HashMap<>();
        activeGuestAttrs.put("virtWhoType", "libvirt");
        activeGuestAttrs.put("active", "1");
    }


    @Test
    public void testFindBestWithSingleProductSinglePoolReturnsProvidedPool() {
        Product product = TestUtil.createProduct(productId, "A test product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
    }

    @Test
    public void testSelectBestPoolsFiltersTooMuchContent() {
        Pool pool = createV3OnlyPool();

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        List<PoolQuantity> poolQs = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);
        assertEquals(0, poolQs.size());

        // Try again with explicitly setting the consumer to cert v1:
        consumer.setFact("system.certificate_version", "1.0");
        poolQs = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);
        assertEquals(0, poolQs.size());
    }

    @Test
    public void testSelectBestPoolsLotsOfContentV2Client() {
        Product mktProduct = TestUtil.createProduct(productId, "A test product");
        Product engProduct = TestUtil.createProduct(Integer.toString(TestUtil.randomInt()), "An ENG product");
    }

    public void testSelectBestPoolsDoesntFilterTooMuchContentForHypervisor() {
        Pool pool = createV3OnlyPool();

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        // Create a hypervisor consumer which does *not* have a certificate version fact.
        // This replicates the real world scenario for virt-who created hypervisors.

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.HYPERVISOR);
        ctype.setId("test-ctype");

        consumer = new Consumer("test consumer", "test user", owner, ctype);

        when(consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()))).thenReturn(ctype);
        when(consumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

        List<PoolQuantity> results = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, results.size());
    }

    @Test
    public void testSelectBestPoolsLotsOfContentV3Client() {
        Pool pool = createV3OnlyPool();

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        // Shouldn't throw an exception as we do for certv1 clients.
        consumer.setFact("system.certificate_version", "3.5");
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);
        assertEquals(1, bestPools.size());
    }

    /*
     * Create a pool with too much content for a V1 certificate, consumer must be V3
     * capable.
     */
    public Pool createV3OnlyPool() {
        Product mktProduct = TestUtil.createProduct(productId, "A test product");
        Product engProduct = TestUtil.createProduct(Integer.toString(TestUtil.randomInt()), "An ENG product");

        engProduct.setProductContent(null);
        for (int i = 0; i < X509ExtensionUtil.V1_CONTENT_LIMIT + 1; i++) {
            Content content = TestUtil.createContent("fake" + i);
            content.setLabel("fake" + i);
            content.setType("yum");
            content.setVendor("vendor");
            content.setContentUrl("");
            content.setGpgUrl("");
            content.setArches("");

            engProduct.addContent(content, true);
        }

        Pool pool = TestUtil.createPool(owner, mktProduct);
        pool.setId("DEAD-BEEFX");

        pool.addProvidedProduct(engProduct);
        when(mockProductCurator.getPoolProvidedProductsCached(pool.getId()))
            .thenReturn(Collections.singleton(engProduct));

        return pool;
    }

    @Test
    public void testFindBestWithConsumerSockets() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.SOCKETS, "4");

        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool, 1)));
    }

    /*
     * Make sure the attribute with the minimum number of pools is chosen
     */
    @Test
    public void testFindBestWithMultiAttrsStacked() {
        consumer.setFact("cpu.cpu_socket(s)", "4");
        consumer.setFact("memory.memtotal", "16000000");
        consumer.setFact("cpu.core(s)_per_socket", "4");

        // Will be common to both SKUs and what we autobind for:
        Product provided = mockProduct("5000", "Eng Product");

        Product sku1 = mockStackingProduct(productId, "Test Stack product", "1", "1");
        sku1.setAttribute(Product.Attributes.CORES, "6");
        sku1.setAttribute(Product.Attributes.RAM, "2");
        sku1.setAttribute(Product.Attributes.SOCKETS, "2");

        Pool pool1 = createPool("DEAD-BEEF1", owner, sku1, provided);

        //only enforce cores on pool 2:
        Product sku2 = mockStackingProduct("prod2", "Test Stack product", "1", "1");
        sku2.setAttribute(Product.Attributes.CORES, "6");
        sku2.setAttribute(Product.Attributes.RAM, null);
        sku2.setAttribute(Product.Attributes.SOCKETS, null);

        Pool pool2 = createPool("DEAD-BEEF2", owner, sku2, provided);
        Pool pool3 = createPool("DEAD-BEEF3", owner, sku1, provided);

        List<Pool> pools = new LinkedList<>();
        pools.add(pool1);
        pools.add(pool2);
        pools.add(pool3);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ provided.getId() }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        assertEquals(pool2, bestPools.get(0).getPool());
        assertEquals(new Integer(3), bestPools.get(0).getQuantity());
    }

    /*
     * Make sure the attribute with the minimum number of pools is chosen
     */
    @Test
    public void testFindBestWithMultiAttrsStackedVirt() {
        consumer.setFact("cpu.cpu_socket(s)", "4");
        consumer.setFact("memory.memtotal", "16000000");
        consumer.setFact("cpu.core(s)_per_socket", "4");
        consumer.setFact("virt.is_guest", "true");

        // Will be common to both SKUs and what we autobind for:
        Product provided = mockProduct("5000", "Eng Product");

        Product sku1 = mockStackingProduct(productId, "Test Stack product", "1", "1");
        sku1.setAttribute(Product.Attributes.CORES, "6");
        sku1.setAttribute(Product.Attributes.RAM, "2");
        sku1.setAttribute(Product.Attributes.SOCKETS, "2");

        Pool pool1 = createPool("DEAD-BEEF1", owner, sku1, provided);
        pool1.setAttribute(Product.Attributes.VIRT_ONLY, "true");

        //only enforce cores on pool 2:
        Product sku2 = mockStackingProduct("prod2", "Test Stack product", "1", "1");
        sku2.setAttribute(Product.Attributes.CORES, "6");

        Pool pool2 = createPool("DEAD-BEEF2", owner, sku2, provided);

        Pool pool3 = createPool("DEAD-BEEF3", owner, sku1, provided);

        List<Pool> pools = new LinkedList<>();
        pools.add(pool1);
        pools.add(pool2);
        pools.add(pool3);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ provided.getId() }, pools, compliance, null, new HashSet<>(), false);

        assertEquals(2, bestPools.size());
        //higher quantity from this pool, as it is virt_only
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 5)));
        assertTrue(bestPools.contains(new PoolQuantity(pool3, 3)));
    }

    /**
     * Creates a Pool and makes sure that Product curator can retrieve
     * the provided products for the pool
     * @param poolId
     * @param owner
     * @param sku
     * @param provided
     * @return
     */
    private Pool createPool(String poolId, Owner owner, Product sku, Product provided) {
        Pool pool = TestUtil.createPool(owner, sku);
        pool.setId(poolId);
        pool.addProvidedProduct(provided);
        when(mockProductCurator.getPoolProvidedProductsCached(poolId))
            .thenReturn(Collections.singleton(provided));
        return pool;
    }

    /*
     * This test assumes that the consumer does not have any existing entitlements.
     */
    @Test
    public void selectBestPoolsDoesNotFilterPoolsBySLAWhenConsumerHasSLASet() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Premium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaStandardPool);

        consumer.setServiceLevel("Premium");

        // The consumer does not have any existing entitlements.

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaPremiumProdId, slaStandardProdId},
            pools, compliance, null, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(slaStandardPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    /*
     * This test assumes that the consumer does not have any existing entitlements.
     */
    @Test
    public void selectBestPoolsDoesNotFilterPoolsBySLAWhenOrgHasDefaultSLASet() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaStandardPool);

        consumer.setServiceLevel("");
        // The Org default SLA is set
        owner.setDefaultServiceLevel("Premium");

        // The consumer does not have any existing entitlements.

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaPremiumProdId, slaStandardProdId},
            pools, compliance, null, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(slaStandardPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    /*
     * This test assumes that the consumer does not have any existing entitlements.
     */
    @Test
    public void selectBestPoolsDoesNotFilterPoolsBySLAWhenSLAOverrideIsSet() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaStandardPool);

        consumer.setServiceLevel("Premium");
        // We have the SLA Override set
        String slaOverride = "Standard";

        // The consumer does not have any existing entitlements.

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaPremiumProdId, slaStandardProdId},
            pools, compliance, slaOverride, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(slaStandardPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    @Test
    public void selectBestPoolsDoesNotFilterPoolsBySLAWhenConsumerHasNonNullMatchingEntitlementSLAs() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);

        // The consumer has set their SLA to Premium, and also has an existing entitlement with Premium SLA
        // which means the pool with Premium SLA should not be filtered.
        consumer.setServiceLevel("Premium");
        Entitlement entitlementWithPremiumSLA = new Entitlement();
        entitlementWithPremiumSLA.setPool(slaPremiumPool);
        compliance.addCompliantProduct("2432", entitlementWithPremiumSLA);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaPremiumProdId },
            pools, compliance, null, new HashSet<>(), false);

        assertEquals(2, bestPools.size());
        // The Premium SLA pool should NOT have gotten filtered because the customer had
        // existing entitlements that included one with a Premium SLA.
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    public void selectBestPoolsFiltersPoolsBySLAWhenConsumerHasNonNullNonMatchingEntitlementSLAsAndConsumerSLAIsUsed() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        // ^ A Standard SLA pool is not in the list of candidate pools.

        // The consumer has set their SLA to Premium, and also has an existing entitlement with Standard SLA
        // which means the pool with Premium SLA SHOULD get filtered.
        consumer.setServiceLevel("Premium");
        Entitlement entitlementWithStandardSLA = new Entitlement();
        entitlementWithStandardSLA.setPool(slaStandardPool);
        compliance.addPartiallyCompliantProduct("b4b4b4", entitlementWithStandardSLA);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaStandardProdId},
            pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        // The Premium SLA pool should get filtered because the customer had existing entitlements that
        // did not include one with a Premium SLA.
        assertFalse(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    public void selectBestPoolsFiltersPoolsBySLAWhenConsumerWithSLAHasNonNullNonMatchingEntitlementSLAsAndOverrideSLAIsUsed() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create Sub-Standard SLA Product
        String slaSubStandardProdId = "sub-standard-sla-product";
        Product slaSubStandardProduct = TestUtil.createProduct(slaSubStandardProdId, "Product with SLA Sub-Standard");
        slaSubStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Sub-Standard");

        Pool slaSubStandardPool = TestUtil.createPool(owner, slaSubStandardProduct);
        slaSubStandardPool.setId("pool-with-sub-standard-sla");
        slaSubStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Sub-Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaSubStandardPool);
        // ^ A Standard SLA pool is not in the list of candidate pools.

        // The consumer has set their SLA to Premium, but will be ignored because we specify an override SLA,
        // and also the consumer has an existing entitlement with Standard SLA
        // which means the candidate pools with Premium SLA and Sub-Standard SLA SHOULD BOTH get filtered.
        consumer.setServiceLevel("Premium");
        Entitlement entitlementWithStandardSLA = new Entitlement();
        entitlementWithStandardSLA.setPool(slaStandardPool);
        compliance.addPartiallyCompliantProduct("b4b4b4", entitlementWithStandardSLA);

        String overrideSLA = "Sub-Standard";

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaStandardProdId},
            pools, compliance, overrideSLA, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        // Both The Premium SLA and Sub-Standard SLA pools should get filtered
        // because the customer had existing entitlements that
        // did not include one with a Premium or Sub-Standard SLA.
        assertFalse(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        assertFalse(bestPools.contains(new PoolQuantity(slaSubStandardPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    public void selectBestPoolsFiltersPoolsBySLAWhenConsumerWithSLAHasNonNullNonMatchingEntitlementSLAsAndOwnerDefaultSLAIsUsed() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        // ^ A Standard SLA pool is not in the list of candidate pools.

        // The consumer has set their SLA to nothing, so it will be ignored in favor of the
        // owner default SLA which is set to Premium,
        // but the consumer also has an existing entitlement with Standard SLA
        // which means the candidate pool with Premium SLA SHOULD get filtered.
        consumer.setServiceLevel("");
        owner.setDefaultServiceLevel("Premium");
        Entitlement entitlementWithStandardSLA = new Entitlement();
        entitlementWithStandardSLA.setPool(slaStandardPool);
        compliance.addPartiallyCompliantProduct("b4b4b4", entitlementWithStandardSLA);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaStandardProdId},
            pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        // The Premium SLA pool should get filtered because the customer had existing entitlements that
        // did not include one with a Premium SLA.
        assertFalse(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    @Test
    public void selectBestPoolsDoesNotFilterPoolsBySLAWhenConsumerHasOnlyNullEntitlementSLAs() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-with-NO-sla-1");

        // Create another product with no SLA.
        String noSLAProdId2 = "no-sla-product2";
        Product noSLAProduct2 = TestUtil.createProduct(noSLAProdId2, "A test product 2");
        Pool noSLAPool2 = TestUtil.createPool(owner, noSLAProduct2);
        noSLAPool2.setId("pool-with-NO-sla-2");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaStandardPool);

        // The consumer has set their SLA to Premium, and also has an existing entitlement with no SLA
        // which means no pools should get filtered.
        consumer.setServiceLevel("Premium");
        Entitlement entitlementWithNoSLA = new Entitlement();
        entitlementWithNoSLA.setPool(noSLAPool2);
        compliance.addPartiallyCompliantProduct("b4b4b4", entitlementWithNoSLA);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaStandardProdId, slaPremiumProdId },
            pools, compliance, null, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        // Check that no pools were filtered, since the customer has an existing entitlement with a null SLA.
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(slaStandardPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    private Product createSysPurposeProduct(String id, String roles, String addons, String supportLevel,
        String usage) {

        Product prod = new Product();

        if (id != null) {
            prod.setId(id);
        }

        if (supportLevel != null) {
            prod.setAttribute(Product.Attributes.SUPPORT_LEVEL, supportLevel);
        }

        if (usage != null) {
            prod.setAttribute(Product.Attributes.USAGE, usage);
        }

        if (roles != null) {
            prod.setAttribute(Product.Attributes.ROLES, roles);
        }

        if (addons != null) {
            prod.setAttribute(Product.Attributes.ADDONS, addons);
        }

        return prod;
    }

    @Test
    public void testSysPurposePoolPriorityCompliantRoleNonCompliantAddon() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product82 = new Product();
        product82.setId("non-compliant-82");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);


        // Consumer satisfied syspurpose attributes:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Server",
            null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, "RHEL Server", "RHEL EUS", null, "Production");
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");
        p1.addProvidedProduct(product69);

        Product prod2 = createSysPurposeProduct(null, null, "RHEL EUS", null, null);
        Pool p2 = TestUtil.createPool(owner, prod2);
        p2.setId("p2");

        Product prod3 = createSysPurposeProduct(null, "JBoss", "RHEL EUS", null, null);
        Pool p3 = TestUtil.createPool(owner, prod3);
        p3.setId("p3");
        p3.addProvidedProduct(product82);

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", p1);
        Double p1Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", p2);
        Double p2Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", p3);
        Double p3Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool p2 should have a higher priority than pool p1.",
            p2Priority > p1Priority);

        assertTrue("Pool p1 should have a higher priority than pool p3.",
            p1Priority > p3Priority);
    }

    @Test
    public void testSysPurposePoolPriorityNonCompliantRoleAndAddon() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product82 = new Product();
        product82.setId("non-compliant-82");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, "RHEL Server", "RHEL EUS", null, "Production");
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");
        p1.addProvidedProduct(product69);

        Product prod2 = createSysPurposeProduct(null, null, "RHEL EUS", null, null);
        Pool p2 = TestUtil.createPool(owner, prod2);
        p2.setId("p2");

        Product prod3 = createSysPurposeProduct(null, "JBoss", "RHEL EUS", null, null);
        Pool p3 = TestUtil.createPool(owner, prod3);
        p3.setId("p3");
        p3.addProvidedProduct(product82);

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", p1);
        Double p1Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", p2);
        Double p2Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", p3);
        Double p3Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool p1 should have a higher priority than pool p2.",
            p1Priority > p2Priority);

        assertTrue("Pool p2 should have a higher priority than pool p3.",
            p2Priority > p3Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase1RoleMatch() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product89 = new Product();
        product89.setId("non-compliant-89");
        Product product100 = new Product();
        product100.setId("non-compliant-100");

        // Consumer specified syspurpose attributes:
        consumer.setRole("Satellite");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management", null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");
        RH00009.addProvidedProduct(product69);

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.addProvidedProduct(product69);
        MCT1650.addProvidedProduct(product89);
        MCT1650.addProvidedProduct(product100);

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT1650);
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool MCT1650 should have a higher priority than pool RH00009.",
            MCT1650Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase2RoleMatch() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product89 = new Product();
        product89.setId("non-compliant-89");
        Product product100 = new Product();
        product100.setId("non-compliant-100");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management", null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");
        RH00009.addProvidedProduct(product69);

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.addProvidedProduct(product69);
        MCT1650.addProvidedProduct(product89);
        MCT1650.addProvidedProduct(product100);

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT1650);
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00009 should have a higher priority than pool MCT1650.",
            RH00009Priority > MCT1650Priority);
    }

    /*
     * This test demonstrates that a pool with no available quantity will not
     * be selected even though it is a better match. In fact, it should not make it pass the filtering stage.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase3MismatchedRoles() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("compliant-69");
        Product product89 = new Product();
        product89.setId("non-compliant-89");
        Product product100 = new Product();
        product100.setId("non-compliant-100");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        ConsumerInstalledProduct consumerInstalledProduct =
            new ConsumerInstalledProduct(product69);
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management", null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");
        RH00009.addProvidedProduct(product69);
        RH00009.setQuantity(0L); // No quantity available

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.addProvidedProduct(product69);
        MCT1650.addProvidedProduct(product89);
        MCT1650.addProvidedProduct(product100);
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(RH00009);
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{"compliant-69"}, pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
        assertFalse(bestPools.contains(new PoolQuantity(RH00009, 0)));
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase4RHELVariants() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product89 = new Product();
        product89.setId("non-compliant-89");
        Product product100 = new Product();
        product100.setId("non-compliant-100");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Workstation");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", null, null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");
        RH00009.addProvidedProduct(product69);

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.addProvidedProduct(product69);
        MCT1650.addProvidedProduct(product89);
        MCT1650.addProvidedProduct(product100);

        Product prodMCT0352 = createSysPurposeProduct(null, "RHEL Workstation", null, null, null);
        Pool MCT0352 = TestUtil.createPool(owner, prodMCT0352);
        MCT0352.setId("MCT0352");
        MCT0352.addProvidedProduct(product69);

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT1650);
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT0352);
        Double MCT0352Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool MCT0352 should have a higher priority than pool RH00009.",
            MCT0352Priority > RH00009Priority);

        assertTrue("Pool RH00009 should have equal priority with pool MCT1650.",
            RH00009Priority.equals(MCT1650Priority));
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase5RHELServerEUS() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // Consumer satisfied role attribute:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Server",
            null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "RHEL EUS", null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");

        Product prodRH00030 = createSysPurposeProduct(null, null, "RHEL EUS", null, null);
        Pool RH00030 = TestUtil.createPool(owner, prodRH00030);
        RH00030.setId("RH00030");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT1650);
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00030);
        Double RH00030Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00030 should have a higher priority than pool RH00009.",
            RH00030Priority > RH00009Priority);

        assertTrue("Pool RH00009 should have a higher priority than pool MCT1650.",
            RH00009Priority > MCT1650Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase6RHELServerEUSELS() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        addons.add("RHEL ELS");
        consumer.setAddOns(addons);

        // Consumer satisfied role attribute:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Server",
            null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management", null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodMCT1963 = createSysPurposeProduct(null, null, "RHEL ELS", null, null);
        Pool MCT1963 = TestUtil.createPool(owner, prodMCT1963);
        MCT1963.setId("MCT1963");

        Product prodRH00030 = createSysPurposeProduct(null, null, "RHEL EUS", null, null);
        Pool RH00030 = TestUtil.createPool(owner, prodRH00030);
        RH00030.setId("RH00030");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT1963);
        Double MCT1963Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00030);
        Double RH00030Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00030 should have a higher priority than pool RH00009.",
            RH00030Priority > RH00009Priority);

        assertTrue("Pool MCT1963 should have a higher priority than pool RH00009.",
            MCT1963Priority > RH00009Priority);

        // Check that both pools would have the same priority.
        assertTrue("Pool MCT1963 should have equal priority with pool MCT1650.",
            MCT1963Priority.equals(RH00030Priority));
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase7ComputeNodeAndEUS() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // Consumer satisfied role attribute:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Server",
            null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management", null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00741 = createSysPurposeProduct(null, "RHEL for HPC Compute Node", "RHEL EUS",
            null, null);
        Pool RH00741 = TestUtil.createPool(owner, prodRH00741);
        RH00741.setId("RH00741");

        Product prodRH00030 = createSysPurposeProduct(null, null, "RHEL EUS", null, null);
        Pool RH00030 = TestUtil.createPool(owner, prodRH00030);
        RH00030.setId("RH00030");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00741);
        Double RH00741Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00030);
        Double RH00030Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00030 should have a higher priority than pool RH00741.",
            RH00030Priority > RH00741Priority);

        assertTrue("Pool RH00741 should have a higher priority than pool RH00009.",
            RH00741Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase8CombinationOfRoleAndAddons() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Workstation");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // Consumer satisfied role attribute:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Workstation",
            null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management", null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodMCT0352 = createSysPurposeProduct(null, "RHEL Workstation", null, null, null);
        Pool MCT0352 = TestUtil.createPool(owner, prodMCT0352);
        MCT0352.setId("MCT0352");

        Product prodRH00030 = createSysPurposeProduct(null, null, "RHEL EUS", null, null);
        Pool RH00030 = TestUtil.createPool(owner, prodRH00030);
        RH00030.setId("RH00030");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT0352);
        Double MCT0352Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00030);
        Double RH00030Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00030 should have a higher priority than pool MCT0352.",
            RH00030Priority > MCT0352Priority);

        assertTrue("Pool MCT0352 should have a higher priority than pool RH00009.",
            MCT0352Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase9PremiumSLADevelopmentUsage() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Development");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Production");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", "Production");
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00008);
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00008 should have a higher priority than pool RH00009.",
            RH00008Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase10RoleMatchesSLABreaksTheTie() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Production");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", "Production");
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00008);
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00008 should have a higher priority than pool RH00009.",
            RH00008Priority > RH00009Priority);
    }

    /*
     * This case demonstrates that between two otherwise equal products,
     * if one product has the SLA defined and it matches the consumer, that product will be used.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase11RoleMatchesSLAGivenNoProductSLA()
        throws NoSuchMethodException {

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Standard");
        consumer.setUsage("Development");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Production");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodI_RH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, "Production");
        Pool I_RH00009 = TestUtil.createPool(owner, prodI_RH00009);
        I_RH00009.setId("I_RH00009");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", I_RH00009);
        Double I_RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00009 should have a higher priority than pool I_RH00009.",
            RH00009Priority > I_RH00009Priority);
    }

    /*
     * The customer has made a typo in the usage. Since the pool that has a
     * defined usage is a mismatch, we favor the pool that has usage undefined.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase17UsageMismatch() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setUsage("Typo");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Development");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, "Premium", null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00008);
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT1650);
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00008 should have a higher priority than pool RH00009.",
            RH00008Priority > RH00009Priority);

        assertTrue("Pool RH00009 should have a higher priority than pool MCT1650.",
            RH00009Priority > MCT1650Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase19OnlyRoleAndSLAareSpecified()
        throws NoSuchMethodException {

        // Consumer specified syspurpose attributes:
        consumer.setRole("Satellite");
        consumer.setServiceLevel("Premium");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Development");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodI_RH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", "Production");
        Pool I_RH00009 = TestUtil.createPool(owner, prodI_RH00009);
        I_RH00009.setId("I_RH00009");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", I_RH00009);
        Double I_RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT1650);
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool MCT1650 should have a higher priority than pool I_RH00009.",
            MCT1650Priority > I_RH00009Priority);

        assertTrue("Pool I_RH00009 should have a higher priority than pool RH00009.",
            I_RH00009Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseSLAOrUsageMatchDoesNotOverpowerRole()
        throws NoSuchMethodException {

        Product product69 = new Product();
        product69.setId("non-compliant-69");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        ConsumerInstalledProduct consumerInstalledProduct =
            new ConsumerInstalledProduct(product69);
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", null, null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");
        RH00009.addProvidedProduct(product69);

        Product prodMCT_HA = createSysPurposeProduct(null, "RHEL High Availability", null,
            "Premium", "Production");
        Pool MCT_HA = TestUtil.createPool(owner, prodMCT_HA);
        MCT_HA.setId("MCT_HA");
        MCT_HA.addProvidedProduct(product69);

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", MCT_HA);
        Double MCT_HAPriority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00009 should have a higher priority than pool MCT_HA.",
            RH00009Priority > MCT_HAPriority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseSLABeatsUsage()
        throws NoSuchMethodException {

        Product product69 = new Product();
        product69.setId("non-compliant-69");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Standard");
        consumer.setUsage("Development");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);
        ConsumerInstalledProduct consumerInstalledProduct =
            new ConsumerInstalledProduct(product69);
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH0000W = createSysPurposeProduct(null, "RHEL Workstation", null, "Standard", null);
        Pool RH0000W = TestUtil.createPool(owner, prodRH0000W);
        RH0000W.setId("RH0000W");
        RH0000W.addProvidedProduct(product69);

        Product prodRH0000D = createSysPurposeProduct(null, "RHEL Desktop", null, null, "Development");
        Pool RH0000D = TestUtil.createPool(owner, prodRH0000D);
        RH0000D.setId("RH0000D");
        RH0000D.addProvidedProduct(product69);

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH0000W);
        Double RH0000WPriority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH0000D);
        Double RH0000DPriority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH0000W should have a higher priority than pool RH0000D.",
            RH0000WPriority > RH0000DPriority);
    }

    /*
     * The RH00009 pool matches the role, but everything else is a mismatch
     * and the RH00008 pool doesn't match the role but matches everything else.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseRoleMismatchOutweighsABunchOfOtherMismatches()
        throws NoSuchMethodException {

        Product product69 = new Product();
        product69.setId("non-compliant-69");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        Set<String> addons = new HashSet<>();
        addons.add("Smart Management");
        consumer.setAddOns(addons);
        ConsumerInstalledProduct consumerInstalledProduct =
            new ConsumerInstalledProduct(product69);
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", null, "Standard", "Development");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");
        RH00009.addProvidedProduct(product69);

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL for HPC Compute Node", "Smart Management",
            "Premium", "Production");
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");
        RH00008.addProvidedProduct(product69);

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", consumer);
        args.put("compliance", compliance);

        args.put("pool", RH00009);
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", RH00008);
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        assertTrue("Pool RH00009 should have a higher priority than pool RH00008.",
            RH00009Priority > RH00008Priority);
    }

    @Test
    public void testFindBestWillNotCompleteAPartialStackFromAnotherId() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        String productId1 = "A";
        String productId2 = "B";
        String productId3 = "C";

        Product product1 = mockStackingProduct(productId1, "Test Stack product", "1", "2");
        Product product2 = mockStackingProduct(productId2, "Test Stack product 2", "2",
            "2");
        Product product3 = mockProduct(productId3, "Test Provided product");

        Pool pool1 = mockPool(product1);
        pool1.setId("DEAD-BEEF");
        pool1.addProvidedProduct(product3);

        Pool pool2 = mockPool(product2);
        pool2.setId("DEAD-BEEF2");
        pool2.addProvidedProduct(product3);

        List<Pool> pools = new LinkedList<>();
        //pools.add(pool1);
        pools.add(pool2);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.setPool(pool1);
        entitlement.setQuantity(2);

        compliance.addPartialStack("1", entitlement);

        List<PoolQuantity> result = autobindRules.selectBestPools(consumer,
            new String[]{ productId2, productId3 },
            pools, compliance, null, new HashSet<>(), false);
        assertNotNull(result);
        // We can make sure the partial stack wasn't completed
        for (PoolQuantity pq : result) {
            if (pq.getPool().getId().equals(pool1.getId())) {
                fail("Should not complete this stack");
            }
        }
    }

    protected Pool createPool(Owner owner, Product product, int quantity, Date startDate, Date endDate) {
        Pool p = TestUtil.createPool(owner, product, quantity);
        p.setId("testpool" + TestUtil.randomInt());
        p.setSourceSubscription(new SourceSubscription("testsub" + TestUtil.randomInt(), "master"));
        p.setStartDate(startDate);
        p.setEndDate(endDate);

        return p;
    }

    @Test
    public void testSelectBestPoolNoPools() {
        // There are no pools for the product in this case:
        assertEquals(0, autobindRules.selectBestPools(consumer,
            new String[] {HIGHEST_QUANTITY_PRODUCT}, new LinkedList<>(), compliance,
            null, new HashSet<>(), false).size());
    }

    @Test
    public void testSelectBestPoolDefaultRule() {
        consumer.setFact("cpu.cpu_socket(s)", "32");
        Product product = TestUtil.createProduct("a-product", "A product for testing");

        Pool pool1 = createPool(owner, product, 5, TestUtil
            .createDate(2000, 02, 26), TestUtil.createDate(2050, 02, 26));
        Pool pool2 = createPool(owner, product, 5, TestUtil
            .createDate(2000, 02, 26), TestUtil.createDate(2060, 02, 26));

        List<Pool> availablePools
            = Arrays.asList(new Pool[] {pool1, pool2});

        List<PoolQuantity> result = autobindRules.selectBestPools(consumer,
            new String[] {product.getId()}, availablePools, compliance, null, new HashSet<>(), false);
        assertNotNull(result);
        for (PoolQuantity pq : result) {
            assertEquals(new Integer(1), pq.getQuantity());
        }
    }

    private Product mockStackingProduct(String pid, String productName,
        String stackId, String sockets) {
        Product product = TestUtil.createProduct(pid, productName);
        product.setAttribute(Product.Attributes.SOCKETS, sockets);
        product.setAttribute(Product.Attributes.STACKING_ID, stackId);
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        return product;
    }

    private Product mockProduct(String pid, String productName) {
        Product product = TestUtil.createProduct(pid, productName);
        product.setUuid("FAKE_DB_ID");
        return product;
    }

    private Product mockProduct(String pid, String productName, String sockets) {
        Product product = TestUtil.createProduct(pid, productName);
        product.setAttribute(Product.Attributes.SOCKETS, sockets);
        return product;
    }

    private Pool mockPool(Product product) {
        Pool p = TestUtil.createPool(owner, product);
        p.setId(TestUtil.randomInt() + "");

        return p;
    }

    private List<Pool> createDerivedPool(String derivedEngPid) {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setUuid("FAKE_DB_ID");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        product.setAttribute(Product.Attributes.SOCKETS, "2");

        Set<Product> derivedProvided = new HashSet<>();
        derivedProvided.add(TestUtil.createProduct(derivedEngPid, derivedEngPid));

        Product derivedProduct = TestUtil.createProduct("derivedProd", "A derived test product");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");

        Pool pool = TestUtil.createPool(
            owner, product, new HashSet<>(), derivedProduct, derivedProvided, 100
        );

        pool.setId("DEAD-BEEF-DER");

        when(mockProductCurator.getPoolDerivedProvidedProductsCached(pool.getId()))
            .thenReturn(derivedProvided);

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    /*
     * Test an autobind where we're asking to give the host entitlements that will
     * help cover the guest. Host has no actual need for the pool otherwise
     */
    @Test
    public void autobindHostToDerivedPoolForGuest() {
        String engProdId = "928";
        List<Pool> pools = createDerivedPool(engProdId);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ engProdId }, pools, compliance, null, new HashSet<>(),
            true);
        assertEquals(1, bestPools.size());
    }

    @Test
    public void instanceAutobindForPhysicalNoSocketFact() {
        List<Pool> pools = createInstanceBasedPool();

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(2), q.getQuantity());
    }

    @Test
    public void instanceAutobindForPhysical8Socket() {
        List<Pool> pools = createInstanceBasedPool();
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(8), q.getQuantity());
    }

    @Test
    public void instanceAutobindForPhysical8SocketNotEnoughUneven() {
        List<Pool> pools = createInstanceBasedPool();
        pools.get(0).setQuantity(7L); // Only 7 available
        setupConsumer("8", false);

        assertEquals(0, autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false).size());
    }

    @Test
    public void instanceAutobindForPhysical8SocketNotEnoughEven() {
        List<Pool> pools = createInstanceBasedPool();
        pools.get(0).setQuantity(4L); // Only 4 available
        setupConsumer("8", false);

        assertEquals(0, autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false).size());
    }

    @Test
    public void instanceAutobindForPhysical8SocketCompletePartialStack() {
        List<Pool> pools = createInstanceBasedPool();
        setupConsumer("8", false);

        // Create a pre-existing entitlement which only covers half of the sockets:
        Entitlement mockEnt = mockEntitlement(pools.get(0), 4);
        consumer.addEntitlement(mockEnt);
        compliance.addPartiallyCompliantProduct(productId, mockEnt);
        compliance.addPartialStack("1", mockEnt);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(4), q.getQuantity());
    }

     // Simple utility to simulate a pre-existing entitlement for a pool.
    private Entitlement mockEntitlement(Pool p, int quantity) {
        Entitlement e = TestUtil.createEntitlement(owner, consumer, p, null);
        e.setQuantity(quantity);
        return e;
    }

    @Test
    public void instanceAutobindForVirt8Vcpu() {
        List<Pool> pools = createInstanceBasedPool();
        setupConsumer("8", true);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(1), q.getQuantity());
    }

    private void setupConsumer(String socketFact, boolean isVirt) {
        this.consumer.setFact("cpu.cpu_socket(s)", socketFact);
        if (isVirt) {
            this.consumer.setFact("virt.is_guest", "true");
        }
    }

    private List<Pool> createInstanceBasedPool() {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "2");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    private List<Pool> createSocketPool(int sockets, int quantity, String stackingId) {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        product.setAttribute(Product.Attributes.SOCKETS, "" + sockets);
        Pool pool = TestUtil.createPool(owner, product, quantity);
        pool.setId("DEAD-BEEF-SOCKETS-" + TestUtil.randomInt());

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    @Test
    public void hostRestrictedAutobindForVirt8Vcpu() {
        List<Pool> pools = createHostRestrictedVirtLimitPool();
        setupConsumer("8", true);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(1), q.getQuantity());
    }

    // Simulating the subpool you would get after a physical system binds:
    private List<Pool> createHostRestrictedVirtLimitPool() {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        pool.setAttribute(Pool.Attributes.REQUIRES_HOST, "BLAH");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    private List<Pool> createStackedPoolEnforcingNothing() {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    // Testing an edge case, stacking ID defined, but no attributes specified to enforce:
    @Test
    public void unenforcedStackedAutobindForPhysical8Socket() {
        List<Pool> pools = createStackedPoolEnforcingNothing();
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(1), q.getQuantity());
    }

    @Test
    public void unlimitedPoolIsPickedUp() {
        Product product = TestUtil.createProduct(productId, "my-prod");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool = TestUtil.createPool(owner, product, -1);
        pool.setId("POOL-ID");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ product.getId() }, pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(new Integer(1), bestPools.get(0).getQuantity());
        assertEquals("POOL-ID", bestPools.get(0).getPool().getId());
    }

    @Test
    public void unlimitedStackedPoolIsPickedUp() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        Product product = mockStackingProduct(productId, "my-prod", "stackid", "2");
        Pool pool = TestUtil.createPool(owner, product, -1);
        pool.setId("POOL-ID");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ product.getId() }, pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(new Integer(4), bestPools.get(0).getQuantity());
        assertEquals("POOL-ID", bestPools.get(0).getPool().getId());
    }

    /*
     * Expect nothing to happen. We cannot bind the hypervisor in order to make
     * the guests compliant, but that'd be a nice feature in the future.
     */
    @Test
    public void guestLimitAutobindNeitherAttached() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, activeGuestAttrs));
        }
        Product server = mockStackingProduct(productId, "some server", "stackid1", "2");
        server.setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        Product hypervisor = mockStackingProduct("hypervisor", "some hypervisor", "stackid2", "2");
        hypervisor.setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        Pool serverPool = TestUtil.createPool(owner, server, 10);
        Pool hyperPool = TestUtil.createPool(owner, hypervisor, 10);
        serverPool.setId("POOL-ID1");
        hyperPool.setId("Pool-ID2");

        List<Pool> pools = new LinkedList<>();
        pools.add(serverPool);
        pools.add(hyperPool);

        assertEquals(0, autobindRules.selectBestPools(consumer,
            new String[]{ server.getUuid() }, pools, compliance, null, new HashSet<>(), false).size());
    }

    /*
     * If the hypervisor is already installed, and at least partially
     * subscribed, autobind will be able to cover the server subscription
     */
    @Test
    public void guestLimitAutobindServerAttached() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, activeGuestAttrs));
        }

        Product server = mockStackingProduct(productId, "some server", "stackid1", "2");
        server.setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        Product hypervisor = mockStackingProduct("hypervisor", "some hypervisor", "stackid2", "2");
        hypervisor.setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        Pool serverPool = TestUtil.createPool(owner, server, 10);
        Pool hyperPool = TestUtil.createPool(owner, hypervisor, 10);
        serverPool.setId("POOL-ID1");
        hyperPool.setId("Pool-ID2");

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.setPool(hyperPool);
        entitlement.setQuantity(4); // compliant

        // The hypervisor must be installed and entitled on the system for autobind
        // to pick up the unlimited guest_limit
        compliance.addCompliantProduct(hypervisor.getId(), entitlement);

        List<Pool> pools = new LinkedList<>();
        pools.add(serverPool);
        pools.add(hyperPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ server.getId() }, pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(new Integer(4), bestPools.get(0).getQuantity());
        assertEquals("POOL-ID1", bestPools.get(0).getPool().getId());
    }

    /*
     * If the hypervisor is already installed, and at least partially
     * subscribed, autobind will be able to cover the server subscription
     */
    @Test
    public void guestLimitAutobindServerAttachedNonStackable() {
        consumer.setFact("cpu.cpu_socket(s)", "2");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, activeGuestAttrs));
        }

        Product server = mockProduct(productId, "some server", "2");
        server.setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        Product hypervisor = mockProduct("hypervisor", "some hypervisor", "2");
        hypervisor.setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        Pool serverPool = TestUtil.createPool(owner, server, 10);
        Pool hyperPool = TestUtil.createPool(owner, hypervisor, 10);
        serverPool.setId("POOL-ID1");
        hyperPool.setId("Pool-ID2");

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.setPool(hyperPool);
        entitlement.setQuantity(1); // compliant

        // The hypervisor must be installed and entitled on the system for autobind
        // to pick up the unlimited guest_limit
        compliance.addCompliantProduct(hypervisor.getId(), entitlement);

        List<Pool> pools = new LinkedList<>();
        pools.add(serverPool);
        pools.add(hyperPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ server.getId() }, pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(new Integer(1), bestPools.get(0).getQuantity());
        assertEquals("POOL-ID1", bestPools.get(0).getPool().getId());
    }

    @Test
    public void testPoolQuantityCompare() {
        Product prod =
            mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool1, 7);
        assertEquals(-1, pq1.compareTo(pq2));
    }

    @Test
    public void testPoolQuantityCompareEqual() {
        Product prod = mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool1, 5);
        assertEquals(0, pq1.compareTo(pq2));
    }

    @Test
    public void testPoolQuantityCompareDiffPool() {
        Product prod = mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        Pool pool2 = TestUtil.createPool(owner, prod, 10);
        pool2.setId("4321");
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool2, 5);
        assertTrue(pq1.compareTo(pq2) != 0);
        assertEquals(pq1.compareTo(pq2), -pq2.compareTo(pq1));
    }

    @Test
    public void testPoolQuantityCompareNullPool() {
        Product prod = mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        Pool pool2 = TestUtil.createPool(owner, prod, 10);
        pool2.setId(null);
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool2, 5);
        assertTrue(pq1.compareTo(pq2) != 0);
        assertEquals(pq1.compareTo(pq2), -pq2.compareTo(pq1));
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitStack() {
        List<Pool> pools = createSocketPool(1, 100, "1");
        pools.addAll(createSocketPool(2, 100, "1"));

        setupConsumer("32", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 socket subscriptions because less are required
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(16), q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitOvercoverageStack() {
        List<Pool> pools = createSocketPool(2, 100, "1");
        pools.addAll(createSocketPool(32, 100, "1"));

        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 socket subscriptions because there is no over-coverage
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(4), q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitBalanceStack() {
        List<Pool> pools = createSocketPool(3, 100, "1");
        pools.addAll(createSocketPool(5, 100, "1"));

        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 3 socket subscription, becuase 3*3 gives 1 socket over-coverage,
        // and 2*5 provides 2 extra sockets.  using 1 quantity is worth .5 sockets
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(3), q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitBalanceQuantityStack() {
        List<Pool> pools = createSocketPool(1, 100, "1");
        pools.addAll(createSocketPool(5, 100, "1"));

        setupConsumer("9", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 5 socket subscriptions over 9 1 socket subscriptions
        // although we're slightly overconsuming, it's better than wasting subs that may be
        // used elsewhere
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(2), q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFit() {
        List<Pool> pools = createSocketPool(1, 100, "1");
        pools.addAll(createSocketPool(2, 100, "2"));

        setupConsumer("32", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 socket subscriptions because less are required
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(16), q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitOvercoverage() {
        List<Pool> pools = createSocketPool(2, 100, "1");
        pools.addAll(createSocketPool(32, 100, "2"));

        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 socket subscriptions because there is no over-coverage
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(4), q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitBalance() {
        List<Pool> pools = createSocketPool(3, 100, "1");
        pools.addAll(createSocketPool(5, 100, "2"));

        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 3 socket subscription, becuase 3*3 gives 1 socket over-coverage,
        // and 2*5 provides 2 extra sockets.  using 1 quantity is worth .5 sockets
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(3), q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitBalanceQuantity() {
        List<Pool> pools = createSocketPool(1, 100, "1");
        pools.addAll(createSocketPool(5, 100, "2"));

        setupConsumer("9", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 5 socket subscriptions over 9 1 socket subscriptions
        // although we're slightly overconsuming, it's better than wasting subs that may be
        // used elsewhere
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(2), q.getQuantity());
    }
}
