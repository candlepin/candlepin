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

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(pool, bestPools.get(0));
    }

    @Test
    public void testFindBestWithConsumerSocketsAndStackingAndMulitplePools() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = new Product(productId, "A test product");
        product.setAttribute("sockets", "1");
        product.setAttribute("stacking_id", "13");
        product.setAttribute("multi-entitlement", "yes");

        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setId("DEAD-BEEF2");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        pools.add(pool2);

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(4, bestPools.size());
        assertEquals(pool, bestPools.get(0));
    }

    @Test
    public void testFindBestWithConsumerSocketsAndStackingAndMulitplePoolsAndMultipleProducts() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = new Product(productId, "A test product");
        product.setAttribute("sockets", "1");
        product.setAttribute("stacking_id", "13");
        product.setAttribute("multi-entitlement", "yes");

        String productId2 = "b product";
        Product product2 = new Product(productId2, "B test product");

        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setId("DEAD-BEEF2");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        Pool pool3 = TestUtil.createPool(owner, product2);
        pool3.setId("DEAD-BEEF3");
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        pools.add(pool2);
        pools.add(pool3);

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(4, bestPools.size());
        assertEquals(pool, bestPools.get(0));
    }

    @Test
    public void testFindBestWithConsumerSocketsAndStacking() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = new Product(productId, "A test product");
        product.setAttribute("sockets", "1");
        product.setAttribute("stacking_id", "13");
        product.setAttribute("multi-entitlement", "yes");

        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(4, bestPools.size());
        assertEquals(pool, bestPools.get(0));
        // assertEquals(Long.valueOf(4), bestPools.get(0).getQuantity());
    }

    @Test
    public void testFindBestWithConsumerSocketsAndStackingNotEnoughSockets() {
        consumer.setFact("cpu.cpu_socket(s)", "32");

        Product product = new Product(productId, "A test product");
        product.setAttribute("sockets", "1");
        product.setAttribute("stacking_id", "13");
        product.setAttribute("multi-entitlement", "yes");

        // createPool creates quanity of 5 by default, so
        // we don't have enough here
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(32, bestPools.size());
        // assertEquals(pool, bestPools.get(0));
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools);

        assertEquals(1, bestPools.size());

        assertEquals(pool2, bestPools.get(0));
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(pool1, bestPools.get(0));
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(pool1, bestPools.get(0));
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(pool2, bestPools.get(0));
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools);

        assertEquals(1, bestPools.size());
        // assertEquals(pool3, bestPools.get(0));
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2, productId3 }, pools);

        assertEquals(1, bestPools.size());
        // assertEquals(pool2, bestPools.get(0));
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer, new String[]{
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId1 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(pool2, bestPools.get(0));
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

        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId1 }, pools);

        assertEquals(1, bestPools.size());
        assertEquals(pool1, bestPools.get(0));
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
