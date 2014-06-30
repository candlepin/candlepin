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
package org.candlepin.policy.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Content;
import org.candlepin.model.DerivedProvidedProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * AutobindRulesTest
 */
public class AutobindRulesTest {
    @Mock private ProductServiceAdapter prodAdapter;
    @Mock private Config config;
    @Mock private RulesCurator rulesCurator;

    private ComplianceStatus compliance;
    private ProductCache productCache;
    private AutobindRules autobindRules; // TODO rename
    private Owner owner;
    private Consumer consumer;
    private String productId = "a-product";

    private static final String HIGHEST_QUANTITY_PRODUCT = "QUANTITY001";
    private Map<String, String> activeGuestAttrs;

    @Before
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        this.productCache = new ProductCache(config, this.prodAdapter);

        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(
            TestDateUtil.date(2010, 1, 1));

        JsRunner jsRules = new JsRunnerProvider(rulesCurator).get();
        autobindRules = new AutobindRules(jsRules, productCache);

        owner = new Owner();
        consumer = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        compliance = new ComplianceStatus();
        activeGuestAttrs = new HashMap<String, String>();
        activeGuestAttrs.put("virtWhoType", "libvirt");
        activeGuestAttrs.put("active", "1");
    }


    @Test
    public void testFindBestWithSingleProductSinglePoolReturnsProvidedPool() {
        Product product = new Product(productId, "A test product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);

        assertEquals(1, bestPools.size());
    }

    @Test
    public void testSelectBestPoolsFiltersTooMuchContent() {
        Product mktProduct = new Product(productId, "A test product");
        Product engProduct = new Product(Integer.toString(TestUtil.randomInt()),
            "An ENG product");

        Set<Content> productContent = new HashSet<Content>();
        for (int i = 0; i < X509ExtensionUtil.V1_CONTENT_LIMIT + 1; i++) {
            productContent.add(new Content("fake" + i, "fake" + i,
                "fake" + i, "yum", "vendor", "", "", ""));
        }

        engProduct.setContent(productContent);
        Pool pool = TestUtil.createPool(owner, mktProduct);
        pool.setId("DEAD-BEEF");
        pool.addProvidedProduct(new ProvidedProduct(engProduct.getId(),
            engProduct.getName()));
        when(this.prodAdapter.getProductById(productId)).thenReturn(mktProduct);
        when(this.prodAdapter.getProductById(engProduct.getId())).thenReturn(engProduct);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        try {
            autobindRules.selectBestPools(consumer,
                new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
                false);
            fail();
        }
        catch (RuntimeException e) {
            // expected
        }

        // Try again with explicitly setting the consumer to cert v1:
        consumer.setFact("system.certificate_version", "1.0");
        try {
            autobindRules.selectBestPools(consumer,
                new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
                false);
            fail();
        }
        catch (RuntimeException e) {
            // expected
        }
    }

    @Test
    public void testSelectBestPoolsLotsOfContentV2Client() {
        Product mktProduct = new Product(productId, "A test product");
        Product engProduct = new Product(Integer.toString(TestUtil.randomInt()),
            "An ENG product");

        Set<Content> productContent = new HashSet<Content>();
        for (int i = 0; i < X509ExtensionUtil.V1_CONTENT_LIMIT + 1; i++) {
            productContent.add(new Content("fake" + i, "fake" + i,
                "fake" + i, "yum", "vendor", "", "", ""));
        }

        engProduct.setContent(productContent);
        Pool pool = TestUtil.createPool(owner, mktProduct);
        pool.setId("DEAD-BEEF");
        pool.addProvidedProduct(new ProvidedProduct(engProduct.getId(),
            engProduct.getName()));
        when(this.prodAdapter.getProductById(productId)).thenReturn(mktProduct);
        when(this.prodAdapter.getProductById(engProduct.getId())).thenReturn(engProduct);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        // Shouldn't throw an exception as we do for certv1 clients.
        consumer.setFact("system.certificate_version", "2.5");
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);
        assertEquals(1, bestPools.size());
    }

    @Test
    public void testFindBestWithConsumerSockets() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = new Product(productId, "A test product");
        product.setAttribute("sockets", "4");

        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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

        Product product = mockStackingProduct(productId, "Test Stack product", "1", "1");
        product.setAttribute("cores", "6");
        product.setAttribute("ram", "2");
        product.setAttribute("sockets", "2");

        Pool pool1 = TestUtil.createPool(owner, product);
        pool1.setId("DEAD-BEEF1");
        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setId("DEAD-BEEF2");
        //only enforce cores on pool 2
        pool2.setProductAttribute("ram", null, productId);
        pool2.setProductAttribute("sockets", null, productId);
        Pool pool3 = TestUtil.createPool(owner, product);
        pool3.setId("DEAD-BEEF3");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);
        pools.add(pool3);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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

        Product product = mockStackingProduct(productId, "Test Stack product", "1", "1");
        product.setAttribute("cores", "6");
        product.setAttribute("ram", "2");
        product.setAttribute("sockets", "2");

        Pool pool1 = TestUtil.createPool(owner, product);
        pool1.setId("DEAD-BEEF1");
        pool1.setAttribute("virt_only", "true");
        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setId("DEAD-BEEF2");
        //only enforce cores on pool 2
        pool2.setProductAttribute("ram", null, productId);
        pool2.setProductAttribute("sockets", null, productId);
        Pool pool3 = TestUtil.createPool(owner, product);
        pool3.setId("DEAD-BEEF3");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);
        pools.add(pool3);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);

        assertEquals(2, bestPools.size());
        //higher quantity from this pool, as it is virt_only
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 5)));
        assertTrue(bestPools.contains(new PoolQuantity(pool3, 3)));
    }

    @Test
    public void ensureSelectBestPoolsFiltersPoolsBySLAWhenConsumerHasSLASet() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = new Product(slaPremiumProdId,
                                         "Product with SLA Permium");
        slaPremiumProduct.setAttribute("support_level", "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.setProductAttribute("support_level", "Premium",
            slaPremiumProdId);

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = new Product(slaStandardProdId,
                                         "Product with SLA Standard");
        slaStandardProduct.setAttribute("support_level", "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.setProductAttribute("support_level", "Standard",
            slaStandardProdId);

        // Create a product with no SLA.
        Product noSLAProduct = new Product(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        // Ensure correct products are returned when requested.
        when(this.prodAdapter.getProductById(productId)).thenReturn(
            noSLAProduct);
        when(this.prodAdapter.getProductById(slaPremiumProdId)).thenReturn(
            slaPremiumProduct);
        when(this.prodAdapter.getProductById(slaStandardProdId)).thenReturn(
            slaStandardProduct);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaStandardPool);

        // SLA filtering only occurs when consumer has SLA set.
        consumer.setServiceLevel("Premium");

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaPremiumProdId, slaStandardProdId},
            pools, compliance, null, new HashSet<String>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
    }

    @Test
    public void ensureSelectBestPoolsFiltersPoolsBySLAWhenOrgHasSLASet() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = new Product(slaPremiumProdId,
                                         "Product with SLA Permium");
        slaPremiumProduct.setAttribute("support_level", "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.setProductAttribute("support_level", "Premium",
            slaPremiumProdId);

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = new Product(slaStandardProdId,
                                         "Product with SLA Standard");
        slaStandardProduct.setAttribute("support_level", "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.setProductAttribute("support_level", "Standard",
            slaStandardProdId);

        // Create a product with no SLA.
        Product noSLAProduct = new Product(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        // Ensure correct products are returned when requested.
        when(this.prodAdapter.getProductById(productId)).thenReturn(
            noSLAProduct);
        when(this.prodAdapter.getProductById(slaPremiumProdId)).thenReturn(
            slaPremiumProduct);
        when(this.prodAdapter.getProductById(slaStandardProdId)).thenReturn(
            slaStandardProduct);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaStandardPool);

        // SLA filtering only occurs when consumer has SLA set.
        consumer.setServiceLevel("");
        consumer.getOwner().setDefaultServiceLevel("Premium");

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId, slaPremiumProdId, slaStandardProdId},
            pools, compliance, null, new HashSet<String>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
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
        pool1.addProvidedProduct(new ProvidedProduct(product3.getId(), product3.getName()));

        Pool pool2 = mockPool(product2);
        pool2.setId("DEAD-BEEF2");
        pool2.addProvidedProduct(new ProvidedProduct(product3.getId(), product3.getName()));

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);
        when(this.prodAdapter.getProductById(productId3)).thenReturn(product3);

        List<Pool> pools = new LinkedList<Pool>();
        //pools.add(pool1);
        pools.add(pool2);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.setPool(pool1);
        entitlement.setQuantity(2);

        compliance.addPartialStack("1", entitlement);

        List<PoolQuantity> result = autobindRules.selectBestPools(consumer,
            new String[]{ productId2, productId3 },
            pools, compliance, null, new HashSet<String>(), false);
        assertNotNull(result);
        // We can make sure the partial stack wasn't completed
        for (PoolQuantity pq : result) {
            if (pq.getPool().getId().equals(pool1.getId())) {
                fail("Should not complete this stack");
            }
        }
    }

    protected Pool createPool(Owner owner, Product product,
        int quantity, Date startDate, Date endDate) {
        Pool p = TestUtil.createPool(owner, product, quantity);
        p.setId("testpool" + TestUtil.randomInt());
        p.setSourceSubscription(
            new SourceSubscription("testsub" + TestUtil.randomInt(), "master"));
        p.setStartDate(startDate);
        p.setEndDate(endDate);
        for (ProductAttribute pa : product.getAttributes()) {
            p.addProductAttribute(new ProductPoolAttribute(pa.getName(),
                pa.getValue(), product.getId()));
        }
        return p;
    }

    @Test(expected = RuntimeException.class)
    public void testSelectBestPoolNoPools() {
        when(this.prodAdapter.getProductById(HIGHEST_QUANTITY_PRODUCT))
            .thenReturn(new Product(HIGHEST_QUANTITY_PRODUCT, HIGHEST_QUANTITY_PRODUCT));

        // There are no pools for the product in this case:
        autobindRules.selectBestPools(consumer,
            new String[] {HIGHEST_QUANTITY_PRODUCT}, new LinkedList<Pool>(), compliance,
            null, new HashSet<String>(), false);
    }

    @Test
    public void testSelectBestPoolDefaultRule() {
        consumer.setFact("cpu.cpu_socket(s)", "32");
        Product product = new Product("a-product", "A product for testing");

        Pool pool1 = createPool(owner, product, 5, TestUtil
            .createDate(2000, 02, 26), TestUtil.createDate(2050, 02, 26));
        Pool pool2 = createPool(owner, product, 5, TestUtil
            .createDate(2000, 02, 26), TestUtil.createDate(2060, 02, 26));

        when(this.prodAdapter.getProductById("a-product"))
            .thenReturn(product);

        List<Pool> availablePools
            = Arrays.asList(new Pool[] {pool1, pool2});

        List<PoolQuantity> result = autobindRules.selectBestPools(consumer,
            new String[] {product.getId()}, availablePools, compliance, null,
            new HashSet<String>(), false);
        assertNotNull(result);
        for (PoolQuantity pq : result) {
            assertEquals(new Integer(1), pq.getQuantity());
        }
    }

    private Product mockStackingProduct(String pid, String productName,
        String stackId, String sockets) {
        Product product = new Product(pid, productName);
        product.setAttribute("sockets", sockets);
        product.setAttribute("stacking_id", stackId);
        product.setAttribute("multi-entitlement", "yes");
        when(this.prodAdapter.getProductById(pid)).thenReturn(product);
        return product;
    }

    private Product mockProduct(String pid, String productName) {
        Product product = new Product(pid, productName);
        when(this.prodAdapter.getProductById(pid)).thenReturn(product);
        return product;
    }

    private Product mockProduct(String pid, String productName, String sockets) {
        Product product = new Product(pid, productName);
        product.setAttribute("sockets", sockets);
        when(this.prodAdapter.getProductById(pid)).thenReturn(product);
        return product;
    }

    private Pool mockPool(Product product) {
        Pool p = TestUtil.createPool(owner, product);
        p.setId(TestUtil.randomInt() + "");
        // Copy all the product attributes onto the pool:
        for (ProductAttribute prodAttr : product.getAttributes()) {
            p.setProductAttribute(prodAttr.getName(), prodAttr.getValue(), product.getId());
        }
        return p;
    }

    private List<Pool> createDerivedPool(String derivedEngPid) {
        Product product = new Product(productId, "A test product");
        product.setAttribute("stacking_id", "1");
        product.setAttribute("multi-entitlement", "yes");
        product.setAttribute("sockets", "2");

        Set<DerivedProvidedProduct> derivedProvided =
            new HashSet<DerivedProvidedProduct>();
        derivedProvided.add(new DerivedProvidedProduct(derivedEngPid, derivedEngPid));

        Product derivedProduct = new Product("derivedProd", "A derived test product");
        product.setAttribute("stacking_id", "1");
        product.setAttribute("multi-entitlement", "yes");

        Pool pool = TestUtil.createPool(owner, product, new HashSet<ProvidedProduct>(),
            derivedProduct.getId(), derivedProvided, 100);

        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        when(this.prodAdapter.getProductById(derivedProduct.getId()))
            .thenReturn(derivedProduct);

        List<Pool> pools = new LinkedList<Pool>();
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
            new String[]{ engProdId }, pools, compliance, null, new HashSet<String>(),
            true);
        assertEquals(1, bestPools.size());
    }

    @Test
    public void instanceAutobindForPhysicalNoSocketFact() {
        List<Pool> pools = createInstanceBasedPool();

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(8), q.getQuantity());
    }

    @Test(expected = RuleExecutionException.class)
    public void instanceAutobindForPhysical8SocketNotEnoughUneven() {
        List<Pool> pools = createInstanceBasedPool();
        pools.get(0).setQuantity(7L); // Only 7 available
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);
    }

    @Test(expected = RuleExecutionException.class)
    public void instanceAutobindForPhysical8SocketNotEnoughEven() {
        List<Pool> pools = createInstanceBasedPool();
        pools.get(0).setQuantity(4L); // Only 4 available
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
        Product product = new Product(productId, "A test product");
        product.setAttribute("instance_multiplier", "2");
        product.setAttribute("stacking_id", "1");
        product.setAttribute("multi-entitlement", "yes");
        product.setAttribute("sockets", "2");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        return pools;
    }

    private List<Pool> createSocketPool(int sockets, int quantity, String stackingId) {
        Product product = new Product(productId, "A test product");
        product.setAttribute("stacking_id", stackingId);
        product.setAttribute("multi-entitlement", "yes");
        product.setAttribute("sockets", "" + sockets);
        Pool pool = TestUtil.createPool(owner, product, quantity);
        pool.setId("DEAD-BEEF-SOCKETS-" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        return pools;
    }

    @Test
    public void hostRestrictedAutobindForVirt8Vcpu() {
        List<Pool> pools = createHostRestrictedVirtLimitPool();
        setupConsumer("8", true);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(1), q.getQuantity());
    }

    // Simulating the subpool you would get after a physical system binds:
    private List<Pool> createHostRestrictedVirtLimitPool() {
        Product product = new Product(productId, "A test product");
        product.setAttribute("virt_limit", "4");
        product.setAttribute("stacking_id", "1");
        product.setAttribute("multi-entitlement", "yes");
        product.setAttribute("sockets", "2");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");
        pool.setAttribute("virt_only", "true");
        pool.setAttribute("requires_host", "BLAH");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        return pools;
    }

    private List<Pool> createStackedPoolEnforcingNothing() {
        Product product = new Product(productId, "A test product");
        product.setAttribute("stacking_id", "1");
        product.setAttribute("multi-entitlement", "yes");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        return pools;
    }

    // Testing an edge case, stacking ID defined, but no attributes specified to enforce:
    @Test
    public void unenforcedStackedAutobindForPhysical8Socket() {
        List<Pool> pools = createStackedPoolEnforcingNothing();
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(1), q.getQuantity());
    }

    @Test
    public void unlimitedPoolIsPickedUp() {
        Product product = new Product(productId, "my-prod");
        product.setAttribute("sockets", "2");
        Pool pool = TestUtil.createPool(owner, product, -1);
        pool.setId("POOL-ID");
        when(this.prodAdapter.getProductById(product.getId())).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ product.getId() }, pools, compliance, null,
            new HashSet<String>(), false);
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

        when(this.prodAdapter.getProductById(product.getId())).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ product.getId() }, pools, compliance, null,
            new HashSet<String>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(new Integer(4), bestPools.get(0).getQuantity());
        assertEquals("POOL-ID", bestPools.get(0).getPool().getId());
    }

    /*
     * Expect nothing to happen. We cannot bind the hypervisor in order to make
     * the guests compliant, but that'd be a nice feature in the future.
     */
    @Test(expected = RuleExecutionException.class)
    public void guestLimitAutobindNeitherAttached() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, activeGuestAttrs));
        }
        Product server = mockStackingProduct(productId, "some server", "stackid1", "2");
        server.setAttribute("guest_limit", "4");
        Product hypervisor =
            mockStackingProduct("hypervisor", "some hypervisor", "stackid2", "2");
        hypervisor.setAttribute("guest_limit", "-1");
        Pool serverPool = TestUtil.createPool(owner, server, 10);
        Pool hyperPool = TestUtil.createPool(owner, hypervisor, 10);
        serverPool.setId("POOL-ID1");
        hyperPool.setId("Pool-ID2");

        when(this.prodAdapter.getProductById(server.getId())).thenReturn(server);
        when(this.prodAdapter.getProductById(hypervisor.getId())).thenReturn(hypervisor);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(serverPool);
        pools.add(hyperPool);

        autobindRules.selectBestPools(consumer,
            new String[]{ server.getId() }, pools, compliance, null,
            new HashSet<String>(), false);
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

        Product server =
            mockStackingProduct(productId, "some server", "stackid1", "2");
        server.setAttribute("guest_limit", "4");
        Product hypervisor =
            mockStackingProduct("hypervisor", "some hypervisor", "stackid2", "2");
        hypervisor.setAttribute("guest_limit", "-1");
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

        when(this.prodAdapter.getProductById(server.getId())).thenReturn(server);
        when(this.prodAdapter.getProductById(hypervisor.getId())).thenReturn(hypervisor);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(serverPool);
        pools.add(hyperPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ server.getId() }, pools, compliance, null,
            new HashSet<String>(), false);
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

        Product server =
            mockProduct(productId, "some server", "2");
        server.setAttribute("guest_limit", "4");
        Product hypervisor =
            mockProduct("hypervisor", "some hypervisor", "2");
        hypervisor.setAttribute("guest_limit", "-1");
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

        when(this.prodAdapter.getProductById(server.getId())).thenReturn(server);
        when(this.prodAdapter.getProductById(hypervisor.getId())).thenReturn(hypervisor);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(serverPool);
        pools.add(hyperPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ server.getId() }, pools, compliance, null,
            new HashSet<String>(), false);
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
        Product prod =
            mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool1, 5);
        assertEquals(0, pq1.compareTo(pq2));
    }

    @Test
    public void testPoolQuantityCompareDiffPool() {
        Product prod =
            mockProduct(productId, "some prod", "2");
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
        Product prod =
            mockProduct(productId, "some prod", "2");
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>(),
            false);

        // Should always pick the 2 5 socket subscriptions over 9 1 socket subscriptions
        // although we're slightly overconsuming, it's better than wasting subs that may be
        // used elsewhere
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(2), q.getQuantity());
    }
}
