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
package org.fedoraproject.candlepin.policy.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductAttribute;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.model.Rules;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.JsRulesProvider;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProductCache;
import org.fedoraproject.candlepin.policy.js.entitlement.EntitlementRules;
import org.fedoraproject.candlepin.policy.js.pool.PoolHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;
import org.fedoraproject.candlepin.util.DateSourceImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18nFactory;

/**
 * DefaultRulesTest
 */
public class DefaultRulesTest {
    private Enforcer enforcer;
    @Mock
    private RulesCurator rulesCurator;
    @Mock
    private ProductServiceAdapter prodAdapter;
    private ReadOnlyProductCache productCache;
    private Owner owner;
    private Consumer consumer;
    private String productId = "a-product";

    @Before
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        URL url = this.getClass().getClassLoader()
            .getResource("rules/default-rules.js");
        InputStreamReader inputStreamReader = new InputStreamReader(
            url.openStream());

        BufferedReader reader = new BufferedReader(inputStreamReader);
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line + "\n");
        }
        reader.close();

        Rules rules = mock(Rules.class);
        when(rules.getRules()).thenReturn(builder.toString());
        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(
            TestDateUtil.date(2010, 1, 1));

        JsRules jsRules = new JsRulesProvider(rulesCurator).get();
        enforcer = new EntitlementRules(new DateSourceImpl(), jsRules,
            prodAdapter, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK));

        owner = new Owner();
        consumer = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        
        productCache = new ReadOnlyProductCache(prodAdapter);
    }

    private Pool createPool(Owner owner, Product product) {
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }

    @Test
    public void testBindForSameProductNotAllowed() {
        Product product = new Product(productId, "A product for testing");
        Pool pool = createPool(owner, product);

        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();

        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
    }

    @Test public void bindWithQuantityNoMultiEntitle() {
        Product product = new Product(productId, "A product for testing");
        Pool pool = createPool(owner, product);
        pool.setQuantity(new Long(100));

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 10)
            .getResult();

        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getResourceKey().contains(
            "multi-entitlement"));
    }

    @Test
    public void testBindFromSameProductAllowedWithMultiEntitlementAttribute() {
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("multi-entitlement", "yes"));
        Pool pool = createPool(owner, product);

        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();

        assertTrue(result.isSuccessful());
        assertFalse(result.hasErrors());
        assertFalse(result.hasErrors());
    }

    @Test
    public void bindFromExhaustedPoolShouldFail() {
        Product product = new Product(productId, "A product for testing");
        Pool pool = TestUtil.createPool(owner, product, 0);
        pool.setId("fakeid" + TestUtil.randomInt());

        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();

        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
    }

    @Test
    public void architectureALLShouldNotGenerateWarnings() {
        Pool pool = setupArchTest("arch", "ALL", "arch", "i686");
        pool.setId("fakeid" + TestUtil.randomInt());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void architectureMismatchShouldGenerateWarning() {
        Pool pool = setupArchTest("arch", "x86_64", "uname.machine", "i686");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void missingConsumerArchitectureShouldGenerateWarning() {
        Pool pool = setupArchTest("arch", "x86_64", "uname.machine", "x86_64");

        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void architectureMatches() {
        Pool pool = setupArchTest("arch", "x86_64", "uname.machine", "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void x86ArchitectureProvidesI386() {
        Pool pool = setupArchTest("arch", "x86", "uname.machine", "i386");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void x86ArchitectureProvidesI586() {
        Pool pool = setupArchTest("arch", "x86", "uname.machine", "i586");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void x86ArchitectureProvidesI686() {
        Pool pool = setupArchTest("arch", "x86", "uname.machine", "i686");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testEmptyUname() {
        Pool pool = setupArchTest("arch", "s390x,x86", "uname.machine", "");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void testEmptyArch() {
        Pool pool = setupArchTest("arch", "", "uname.machine", "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void testDuplicateArchesMatches() {
        Pool pool = setupArchTest("arch", "x86_64,x86_64", "uname.machine",
            "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testDuplicateArchesNoMatches() {
        Pool pool = setupArchTest("arch", "x86_64,x86_64", "uname.machine",
            "z80");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void testCommaSplitArchesTrailingComma() {
        Pool pool = setupArchTest("arch", "x86_64,x86_64,", "uname.machine",
            "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testCommaSplitArchesExtraSpaces() {
        Pool pool = setupArchTest("arch", "x86_64,  z80 ", "uname.machine",
            "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void multipleArchesNoMatches() {
        Pool pool = setupArchTest("arch", "s390x,z80,ppc64", "uname.machine",
            "i686");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void multipleArchesMatches() {
        Pool pool = setupArchTest("arch", "s390x,x86", "uname.machine", "i686");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void goodArchNoUnameMachine() {
        Pool pool = setupArchTest("arch", "x86", "something.not.uname", "i686");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void fewerThanMaximumNumberOfSocketsShouldNotGenerateWarning() {
        Pool pool = setupArchTest("sockets", "128", "cpu.cpu_socket(s)", "2");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void matchingNumberOfSocketsShouldNotGenerateWarning() {
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "2");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void missingConsumerSocketsShouldGenerateWarning() {
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "2");

        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void testZeroConsumerSocketsShouldGenerateWarning() {
        Pool pool = setupArchTest("sockets", "0", "cpu.cpu_socket(s)", "2");

        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    private Pool setupArchTest(final String attributeName,
        String attributeValue, final String factName, final String factValue) {

        Product product = new Product(productId, "A product for testing");
        product
            .addAttribute(new ProductAttribute(attributeName, attributeValue));
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());

        consumer.setFacts(new HashMap<String, String>() {
            {
                put(factName, factValue);
            }
        });

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    @Test
    public void exceedingNumberOfSocketsShouldGenerateWarning() {
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_sockets", "4");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void correctConsumerTypeShouldNotGenerateError() {
        Pool pool = setupProductWithRequiresConsumerTypeAttribute();
        consumer.setType(new ConsumerType(ConsumerTypeEnum.DOMAIN));

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void mismatchingConsumerTypeShouldGenerateError() {
        Pool pool = setupProductWithRequiresConsumerTypeAttribute();
        consumer.setType(new ConsumerType(ConsumerTypeEnum.PERSON));

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    private Pool setupProductWithRequiresConsumerTypeAttribute() {
        Product product = new Product(productId, "A product for testing");
        product.setAttribute("requires_consumer_type",
            ConsumerTypeEnum.DOMAIN.toString());
        Pool pool = createPool(owner, product);
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    @Test
    public void userLicensePassesPre() {
        Pool pool = setupUserLicensedPool();
        consumer.setType(new ConsumerType(ConsumerTypeEnum.PERSON));
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void userLicensePostCreatesSubPool() {
        Pool pool = setupUserLicensedPool();
        consumer.setType(new ConsumerType(ConsumerTypeEnum.PERSON));
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            1);

        PoolHelper postHelper = mock(PoolHelper.class);
        enforcer.postEntitlement(consumer, postHelper, e);
        verify(postHelper).createUserRestrictedPool(pool.getProductId(), pool,
            "unlimited");
    }

    @Test
    public void testUserLicensePostForDifferentProduct() {
        Pool pool = setupUserLicensedPool();
        String subProductId = "subProductId";
        pool.setAttribute("user_license_product", subProductId);

        consumer.setType(new ConsumerType(ConsumerTypeEnum.PERSON));
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            1);

        PoolHelper postHelper = mock(PoolHelper.class);
        enforcer.postEntitlement(consumer, postHelper, e);
        verify(postHelper).createUserRestrictedPool(subProductId, pool,
            "unlimited");
    }

    private Pool setupUserLicensedPool() {
        Product product = new Product(productId, "A user licensed product");
        product.setAttribute("requires_consumer_type",
            ConsumerTypeEnum.PERSON.toString());
        Pool pool = createPool(owner, product);
        pool.setAttribute("user_license", "unlimited");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    @Test
    public void userRestrictedPoolPassesPre() {
        Pool pool = setupUserRestrictedPool();
        consumer.setUsername("bob");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void userRestrictedPoolFailsPre() {
        Pool pool = setupUserRestrictedPool();
        consumer.setUsername("notbob");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testFindBestWithSingleProductSinglePoolReturnsProvidedPool() {
        Product product = new Product(productId, "A test product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

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

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(pool).intValue());
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
    public void testFindBestWithConsumerSocketsAndStackingAndMulitplePools() {
        consumer.setFact("cpu.cpu_socket(s)", "4");
        
        Product product = mockStackingProduct(productId, "A test product", "13", "1");

        Pool pool = mockPool(product);
        Pool pool2 = mockPool(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        pools.add(pool2);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(4, bestPools.get(pool).intValue());
    }

    @Test
    public void testFindBestConsumerSocketsAndStackingAndMulitplePoolsMultipleProducts() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = mockStackingProduct(productId, "A test product", "13", "1");

        // Make a non-stacked product:
        String productId2 = "b product";
        Product product2 = new Product(productId2, "B test product");
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        Pool pool = mockPool(product);

        Pool pool2 = mockPool(product);

        Pool pool3 = mockPool(product2);
        pool3.setId("DEAD-BEEF3");

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        pools.add(pool2);
        pools.add(pool3);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertTrue(bestPools.containsKey(pool));
        assertEquals(4, bestPools.get(pool).intValue());
    }

    @Test
    public void selectBestPoolsRegularAndStackingRequested() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = mockStackingProduct(productId, "A test product zippy", "13", "1");

        // Make a non-stacked product:
        String productId2 = "b product";
        Product product2 = new Product(productId2, "B test product");
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        Pool stackedPool = mockPool(product);
        stackedPool.setId("DEAD-BEEF");

        Pool nonStackedPool = mockPool(product2);
        nonStackedPool.setId("DEAD-BEEF3");

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(stackedPool);
        pools.add(nonStackedPool);

        // System has both the stacked product, as well as another non-stacked product,
        // we should be able to auto-subscribe to both:
        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId2, productId }, pools);

        assertEquals(1, bestPools.get(nonStackedPool).intValue());
        
        // check first to avoid NPE
        assertTrue(bestPools.containsKey(stackedPool));
        assertEquals(4, bestPools.get(stackedPool).intValue());
    }
    
    // Test a system requesting a *provided* product, when pools provide it, each
    // with a different stack ID.
    @Test
    public void selectBestPoolsTwoStacksProvideRequestedProduct() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = mockStackingProduct(productId, "Test Product 1", "13", "1");

        // In this case the system will request a provided product, when two pools
        String providedProductId = "providedProductId";
        mockProduct(providedProductId, "Provided Name");
            
        Pool pool = mockPool(product);
        pool.addProvidedProduct(new ProvidedProduct(providedProductId, "Irrelevant Name"));
        pool.setId("DEAD-BEEF");

        Pool pool2 = mockPool(product);
        pool2.setId("DEAD-BEEF3");
        pool2.addProvidedProduct(new ProvidedProduct(providedProductId, "Irrelevant Name"));

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        pools.add(pool2);

        // System has both the stacked product, as well as another non-stacked product,
        // we should be able to auto-subscribe to both:
        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ providedProductId }, pools);

        assertEquals(4, bestPools.get(pool).intValue());
    }

    @Test
    public void testFindBestWithConsumerSocketsAndStacking() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = mockStackingProduct(productId, "A test product", "13", "1");
        
        Pool pool = mockPool(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(4, bestPools.get(pool).intValue());
    }

    @Test
    public void testFindBestWithConsumerSocketsAndStackingNotEnoughSockets() {
        consumer.setFact("cpu.cpu_socket(s)", "32");

        Product product = mockStackingProduct(productId, "A test product", "13", "1");

        Pool pool = mockPool(product);
        pool.setQuantity(5L);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        // we should consume as many as possible, even if this doesnt fully entitle
        assertEquals(1, bestPools.size());
        assertEquals(5, bestPools.get(pool).intValue());
    }

    @Test
    public void testFindBestWithStackingOverEntitlesToFullyCover() {
        consumer.setFact("cpu.cpu_socket(s)", "32");

        Product product = mockStackingProduct(productId, "A test product", "13", "3");

        Pool pool = mockPool(product);
        pool.setQuantity(15L);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(11, bestPools.get(pool).intValue());
    }
    
    @Test
    public void testFindBestRespectsArchitecture() {
        Product product = new Product(productId, "A test product");
        ProductAttribute pa = new ProductAttribute("arch", "x86");
        product.addAttribute(pa);
        consumer.setFact("uname.machine", "i586");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());
    }

    @Test
    public void testFindBestRejectsBasedOnArchitecture() {
        Product product = new Product(productId, "A test product");
        ProductAttribute pa = new ProductAttribute("arch", "x86");
        product.addAttribute(pa);
        consumer.setFact("uname.machine", "x86_64");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<Pool> bestPools = new ArrayList<Pool>();

        try {
            enforcer
                .selectBestPools(consumer, new String[]{ productId }, pools);
        }
        catch (Exception e) {
            // eatit
        }

        assertEquals(0, bestPools.size());
    }

    @Test
    public void testFindBestRejectsWithNoArchitecture() {
        Product product = new Product(productId, "A test product");
        ProductAttribute pa = new ProductAttribute("arch", "x86");
        product.addAttribute(pa);
        consumer.setFact("uname.machine", "x86_64");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<Pool> bestPools = new ArrayList<Pool>();

        try {
            enforcer
                .selectBestPools(consumer, new String[]{ productId }, pools);
        }
        catch (Exception e) {
            // eatit
        }

        assertEquals(0, bestPools.size());
    }

    @Test
    public void testFindBestWithSingleProductTwoPoolsReturnsSinglePool() {
        Product product = new Product(productId, "A test product");
        Pool pool1 = TestUtil.createPool(owner, product);
        pool1.setId("DEAD-BEEF");
        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setId("DEAD-BEEF2");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());
    }

    @Test
    public void testFindBestWithSingleProductTwoPoolsReturnsPoolThatExpiresFirst() {
        Product product = new Product(productId, "A test product");
        Pool pool1 = TestUtil.createPool(owner, product);
        pool1.setId("DEAD-BEEF");

        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setId("DEAD-BEEF2");
        pool2.setEndDate(TestUtil.createDate(2015, 1, 1));

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());

        assertEquals(1, bestPools.get(pool2).intValue());
    }

    @Test
    public void testFindBestWithTwoProductsOnePoolDoesNotFailIfPoolDoesntProvideBoth() {
        String productId1 = "ABB";
        String productId2 = "DEE";

        Product product1 = new Product(productId1, "A test product");
        Product product2 = new Product(productId2, "A test product");
        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(pool1).intValue());
    }

    @Test
    public void testFindBestWithTwoProductsOnePoolPassesForPoolThatProvidesBoth() {
        String productId1 = "ABB";
        String productId2 = "DEE";

        Product product1 = new Product(productId1, "A test product");
        Product product2 = new Product(productId2, "A test product");
        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        providedProducts.add(new ProvidedProduct(product2.getId(), product2
            .getName()));
        pool1.setProvidedProducts(providedProducts);

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(pool1).intValue());
    }

    @Test
    public void testFindBestWithTwoProductsTwoPoolsSelectsPoolThatExpiresFirst() {
        String productId1 = "ABB";
        String productId2 = "DEE";

        Product product1 = new Product(productId1, "A test product");
        Product product2 = new Product(productId2, "A test product");
        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");

        Pool pool2 = TestUtil.createPool(owner, product1);
        pool2.setId("DEAD-BEEF2");
        pool2.setEndDate(TestUtil.createDate(2015, 1, 1));

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        providedProducts.add(new ProvidedProduct(product2.getId(), product2
            .getName()));
        pool1.setProvidedProducts(providedProducts);
        pool2.setProvidedProducts(providedProducts);

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(pool2).intValue());
    }

    @Test
    public void testFindBestWithTwoProductsThreePoolsReturnsPoolThatProvidesBoth() {
        String productId1 = "ABB";
        String productId2 = "DEE";

        Product product1 = new Product(productId1, "A test product");
        Product product2 = new Product(productId2, "A test product");

        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");

        Pool pool2 = TestUtil.createPool(owner, product1);
        pool2.setId("DEAD-BEEF2");

        Pool pool3 = TestUtil.createPool(owner, product1);
        pool3.setId("DEAD-BEEF3");
        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        providedProducts.add(new ProvidedProduct(product2.getId(), product2
            .getName()));
        pool3.setProvidedProducts(providedProducts);

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);
        pools.add(pool3);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(pool3).intValue());
    }

    @Test
    public void testFindBestWithOverlappingPoolsReturnsOnlyOnePool() {
        String productId1 = "ABB";
        String productId2 = "DEE";
        String productId3 = "CED";

        Product product1 = new Product(productId1, "A test product");
        Product product2 = new Product(productId2, "A test product");
        Product product3 = new Product(productId3, "A test product");

        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");

        Pool pool2 = TestUtil.createPool(owner, product3);
        pool2.setId("DEAD-BEEF2");

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        providedProducts.add(new ProvidedProduct(product2.getId(), product2
            .getName()));
        pool1.setProvidedProducts(providedProducts);
        pool2.setProvidedProducts(providedProducts);

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);
        when(this.prodAdapter.getProductById(productId3)).thenReturn(product3);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2, productId3 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(pool2).intValue());
    }

    @Test
    public void testFindBestWithOverlappingPoolsReturnsBothWithMultiEntitle() {
        String productId1 = "ABB";
        String productId2 = "DEE";
        String productId3 = "CED";

        Product product1 = new Product(productId1, "A test product1");
        Product product2 = new Product(productId2, "A test product2");
        Product product3 = new Product(productId3, "A test product3");

        product2.setAttribute("multi-entitlement", "yes");

        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");

        Pool pool2 = TestUtil.createPool(owner, product3);
        pool2.setId("DEAD-BEEF2");

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        providedProducts.add(new ProvidedProduct(product2.getId(), product2
            .getName()));
        pool1.setProvidedProducts(providedProducts);
        pool2.setProvidedProducts(providedProducts);

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);
        when(this.prodAdapter.getProductById(productId3)).thenReturn(product3);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2, productId3 }, pools);

        assertEquals(2, bestPools.size());
    }

    @Test
    public void testFindBestWithTwoPoolsPrefersVirt() {
        String productId1 = "A";

        Product product1 = new Product(productId1, "A test product");

        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");

        Pool pool2 = TestUtil.createPool(owner, product1);
        pool2.setId("DEAD-BEEF2");
        pool2.setAttribute("virt_only", "true");

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId1 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(pool2).intValue());
    }

    @Test
    public void testFindBestWithTwoPoolsBothVirtPrefersExpiry() {
        String productId1 = "A";

        Product product1 = new Product(productId1, "A test product");

        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");
        pool1.setAttribute("virt_only", "true");

        Pool pool2 = TestUtil.createPool(owner, product1);
        pool2.setId("DEAD-BEEF2");
        pool2.setAttribute("virt_only", "true");
        pool2.setEndDate(TestUtil.createDate(2099, 12, 14));

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        Map<Pool, Integer> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId1 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(pool1).intValue());
    }

    private Pool setupUserRestrictedPool() {
        Product product = new Product(productId, "A user restricted product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setRestrictedToUsername("bob");
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

}
