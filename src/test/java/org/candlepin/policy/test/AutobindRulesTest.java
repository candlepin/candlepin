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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

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
                new String[]{ productId }, pools, compliance, null, new HashSet<String>());
            fail();
        }
        catch (RuntimeException e) {
            // expected
        }

        // Try again with explicitly setting the consumer to cert v1:
        consumer.setFact("system.certificate_version", "1.0");
        try {
            autobindRules.selectBestPools(consumer,
                new String[]{ productId }, pools, compliance, null, new HashSet<String>());
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool, 1)));
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
            pools, compliance, null, new HashSet<String>());

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
            pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
    }

    // we shouldn't be able to get any new entitlements
    @Test(expected = RuleExecutionException.class)
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

        autobindRules.selectBestPools(consumer, new String[]{ productId2, productId3 },
            pools, compliance, null, new HashSet<String>());
    }

    protected Pool createPool(Owner owner, Product product,
        int quantity, Date startDate, Date endDate) {
        Pool p = TestUtil.createPool(owner, product, quantity);
        p.setId("testpool" + TestUtil.randomInt());
        p.setSubscriptionId("testsub" + TestUtil.randomInt());
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
            null, new HashSet<String>());
    }

    @Test
    public void testSelectBestPoolDefaultRule() {
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
            new HashSet<String>());
        assertTrue(result.contains(new PoolQuantity(pool1, 1)));
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

    private Pool mockPool(Product product) {
        Pool p = TestUtil.createPool(owner, product);
        p.setId(TestUtil.randomInt() + "");
        // Copy all the product attributes onto the pool:
        for (ProductAttribute prodAttr : product.getAttributes()) {
            p.setProductAttribute(prodAttr.getName(), prodAttr.getValue(), product.getId());
        }
        return p;
    }

    @Test
    public void instanceAutobindForPhysicalNoSocketFact() {
        List<Pool> pools = createInstanceBasedPool();

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(2), q.getQuantity());
    }

    @Test
    public void instanceAutobindForPhysical8Socket() {
        List<Pool> pools = createInstanceBasedPool();
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(8), q.getQuantity());
    }

    @Test
    public void instanceAutobindForPhysical8SocketNotEnoughUneven() {
        List<Pool> pools = createInstanceBasedPool();
        pools.get(0).setQuantity(7L); // Only 7 available
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(6), q.getQuantity());
    }

    @Test
    public void instanceAutobindForPhysical8SocketNotEnoughEven() {
        List<Pool> pools = createInstanceBasedPool();
        pools.get(0).setQuantity(4L); // Only 4 available
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(4), q.getQuantity());
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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

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

    @Test
    public void hostRestrictedAutobindForVirt8Vcpu() {
        List<Pool> pools = createHostRestrictedVirtLimitPool();
        setupConsumer("8", true);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

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
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(new Integer(1), q.getQuantity());
    }

}
