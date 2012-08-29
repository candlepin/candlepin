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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.Config;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.Subscription;
import org.candlepin.policy.Enforcer;
import org.candlepin.policy.PoolRules;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRules;
import org.candlepin.policy.js.JsRulesProvider;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.policy.js.entitlement.ManifestEntitlementRules;
import org.candlepin.policy.js.pool.JsPoolRules;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.X509ExtensionUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18nFactory;

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

/**
 * DefaultRulesTest
 */
public class DefaultRulesTest {
    private Enforcer enforcer;
    @Mock
    private RulesCurator rulesCurator;
    @Mock
    private ProductServiceAdapter prodAdapter;
    @Mock
    private Config config;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock private ComplianceStatus compliance;
    @Mock private PoolManager poolManagerMock;
    private Owner owner;
    private Consumer consumer;
    private String productId = "a-product";
    private PoolRules poolRules;
    private ProductCache productCache;

    @Before
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        this.productCache = new ProductCache(this.prodAdapter);

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
            productCache, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK), config, consumerCurator);

        owner = new Owner();
        consumer = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));

        poolRules = new JsPoolRules(new JsRulesProvider(rulesCurator).get(),
            poolManagerMock,
            productCache, config);

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
    public void manifestConsumerCannotBindToDerivedPool() {
        when(config.standalone()).thenReturn(false);
        Consumer c = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Enforcer enf = new ManifestEntitlementRules(new DateSourceImpl(),
            new JsRulesProvider(rulesCurator).get(),
            productCache, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK), config, consumerCurator);

        Product product = new Product(productId, "A product for testing");
        Pool pool = createPool(owner, product);
        pool.setAttribute("pool_derived", "true");

        Entitlement e = new Entitlement(pool, c, new Date(), new Date(),
            1);
        c.addEntitlement(e);

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enf.preEntitlement(c, pool, 1)
            .getResult();

        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getResourceKey().contains("manifest"));
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
    public void missingConsumerArchitectureShouldNotGenerateWarningForNonSystem() {

        String nonSystemType = "somethingElse";
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("arch", "x86_64"));
        product.setAttribute("requires_consumer_type", nonSystemType);
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());
        consumer.setType(new ConsumerType(nonSystemType));

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
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
    public void missingConsumerSocketsShouldNotGenerateWarning() {
        // non-system consumers do not have socket counts, no warning
        // should be generated (per IT)
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "2");

        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testZeroConsumerSocketsShouldNotGenerateWarning() {
        // there was a bug in an IT adapter where a null socket count was being
        // set to zero. As a hotfix, we do not generate a warning when socket
        // count is zero.
        Pool pool = setupArchTest("sockets", "0", "cpu.cpu_socket(s)", "2");

        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
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
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "4");

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
    public void virtOnlyPoolGuestHostMatches() {
        Consumer parent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Pool pool = setupHostRestrictedPool(parent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId)).thenReturn(parent);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    // There shouldn't be any way to get a host restricted pool in hosted, but make sure
    // if it were to happen, it wouldn't be enforced.
    @Test
    public void hostedVirtOnlyPoolGuestHostDoesNotMatch() {
        Consumer parent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Pool pool = setupHostRestrictedPool(parent);
        Consumer otherParent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));

        when(config.standalone()).thenReturn(false);
        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId)).thenReturn(otherParent);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void virtOnlyPoolGuestHostDoesNotMatch() {
        when(config.standalone()).thenReturn(true);
        // Parent consumer of our guest:
        Consumer parent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));

        // Another parent we'll make a virt only pool for:
        Consumer otherParent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Pool pool = setupHostRestrictedPool(otherParent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId)).thenReturn(parent);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals("virt.guest.host.does.not.match.pool.owner",
            result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void virtOnlyPoolGuestNoHost() {
        when(config.standalone()).thenReturn(true);

        // Another parent we'll make a virt only pool for:
        Consumer otherParent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Pool pool = setupHostRestrictedPool(otherParent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId)).thenReturn(null);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals("virt.guest.host.does.not.match.pool.owner",
            result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void standaloneParentConsumerPostCreatesSubPool() {
        Pool pool = setupVirtLimitPool();
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            5);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(config.standalone()).thenReturn(true);
        enforcer.postEntitlement(consumer, postHelper, e);

        // Pool quantity should be virt_limit * entitlement quantity:
        verify(postHelper).createHostRestrictedPool(eq(pool.getProductId()),
            eq(pool), eq("50"));
    }

    @Test
    public void standaloneParentConsumerPostCreatesUnlimitedSubPool() {
        Pool pool = setupVirtLimitPool();
        pool.setAttribute("virt_limit", "unlimited");
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            5);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(config.standalone()).thenReturn(true);
        enforcer.postEntitlement(consumer, postHelper, e);

        // Pool quantity should be virt_limit * entitlement quantity:
        verify(postHelper).createHostRestrictedPool(eq(pool.getProductId()),
            eq(pool), eq("unlimited"));
    }

    @Test
    public void hostedParentConsumerPostCreatesNoPool() {
        Pool pool = setupVirtLimitPool();
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            1);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(config.standalone()).thenReturn(false);
        enforcer.postEntitlement(consumer, postHelper, e);

        verify(postHelper, never()).createHostRestrictedPool(pool.getProductId(),
            pool, pool.getAttributeValue("virt_limit"));
    }

    @Test
    public void hostedVirtLimitAltersBonusPoolQuantity() {
        when(config.standalone()).thenReturn(false);
        Consumer c = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Enforcer enf = new ManifestEntitlementRules(new DateSourceImpl(),
            new JsRulesProvider(rulesCurator).get(),
            productCache, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK), config, consumerCurator);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "10");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(new Long(100), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("10", virtBonusPool.getProductAttribute("virt_limit").getValue());

        Entitlement e = new Entitlement(physicalPool, c, new Date(), new Date(),
            1);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        enf.postEntitlement(c, postHelper, e);
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool), eq(-10L));

        enf.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool), eq(10L));
    }

    @Test
    public void hostedVirtLimitUnlimitedBonusPoolQuantity() {
        when(config.standalone()).thenReturn(false);
        Consumer c = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Enforcer enf = new ManifestEntitlementRules(new DateSourceImpl(),
            new JsRulesProvider(rulesCurator).get(),
            productCache, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK), config, consumerCurator);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("unlimited", virtBonusPool.getProductAttribute("virt_limit")
            .getValue());

        Entitlement e = new Entitlement(physicalPool, c, new Date(), new Date(),
            1);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        enf.postEntitlement(c, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enf.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
    }

    @Test
    public void hostedVirtLimitBadValueDoesntTraceBack() {
        when(config.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "badvalue");

        List<Pool> pools = null;
        try {
            pools = poolRules.createPools(s);
        }
        catch (Exception e) {
            fail();
        }
        assertEquals(1, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());
    }

    @Test
    public void exportAllPhysicalZeroBonusPoolQuantity() {
        when(config.standalone()).thenReturn(false);
        Consumer c = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Enforcer enf = new ManifestEntitlementRules(new DateSourceImpl(),
            new JsRulesProvider(rulesCurator).get(),
            productCache, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK), config, consumerCurator);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("unlimited", virtBonusPool.getProductAttribute("virt_limit")
            .getValue());

        Entitlement e = new Entitlement(physicalPool, c, new Date(), new Date(),
            10);
        physicalPool.setConsumed(10L);
        physicalPool.setExported(10L);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        enf.postEntitlement(c, postHelper, e);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(0L));
        virtBonusPool.setQuantity(0L);

        enf.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(-1L));
    }

    private Subscription createVirtLimitSub(String productId,
                                            int quantity,
                                            String virtLimit) {
        Product product = new Product(productId, productId);
        product.setAttribute("virt_limit", virtLimit);
        when(prodAdapter.getProductById(productId)).thenReturn(product);
        Subscription s = TestUtil.createSubscription(product);
        s.setQuantity(new Long(quantity));
        s.setId("subId");
        return s;
    }

    @Test
    public void testFindBestWithSingleProductSinglePoolReturnsProvidedPool() {
        Product product = new Product(productId, "A test product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("DEAD-BEEF");
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
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
                "fake" + i, "yum", "vendor", "", ""));
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
            enforcer.selectBestPools(consumer,
                new String[]{ productId }, pools, compliance, null, new HashSet<String>());
            fail();
        }
        catch (RuntimeException e) {
            // expected
        }

        // Try again with explicitly setting the consumer to cert v1:
        consumer.setFact("system.certificate_version", "1.0");
        try {
            enforcer.selectBestPools(consumer,
                new String[]{ productId }, pools, compliance, null, new HashSet<String>());
            fail();
        }
        catch (RuntimeException e) {
            // expected
        }

        // And again with cert v2:
        consumer.setFact("system.certificate_version", "2.5");
        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId, slaPremiumProdId, slaStandardProdId},
            pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool, 4)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertTrue(bestPools.contains(new PoolQuantity(pool, 4)));
    }

    /*
     * Two pools providing the exact same products, but one is virt_only and requires_host,
     * while the other is just virt_only.
     */
    @Test
    public void selectBestPoolsPrefersVirtOnlyHostRestricted() {
        String productId1 = "A";

        Product product1 = new Product(productId1, "A test product");

        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");
        pool1.setAttribute("virt_only", "true");

        Pool pool2 = TestUtil.createPool(owner, product1);
        pool2.setId("DEAD-BEEF2");
        pool2.setAttribute("virt_only", "true");
        pool2.setAttribute("requires_host", "HOSTUUID");

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId1 }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
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
        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId2, productId }, pools, compliance, null,
            new HashSet<String>());

        assertTrue(bestPools.contains(new PoolQuantity(nonStackedPool, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(stackedPool, 4)));
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
        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ providedProductId }, pools, compliance, null,
            new HashSet<String>());

        assertTrue(bestPools.contains(new PoolQuantity(pool, 4)));
    }

    /*
     * ensure that if a system requests a stacking product, it will get entitlements from
     * more than one pool, if neither pool provides enough entitlements alone.
     */
    @Test
    public void selectBestPoolsWithStackingWillUseMultiplePools() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = mockStackingProduct(productId, "Test Product 1", "13", "1");

        Pool pool = mockPool(product);
        pool.setId("DEAD-BEEF");
        pool.setQuantity(3L);

        Pool pool2 = mockPool(product);
        pool2.setId("DEAD-BEEF3");
        pool2.setQuantity(1L);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        pools.add(pool2);

        // System has both the stacked product, as well as another non-stacked product,
        // we should be able to auto-subscribe to both:
        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null,
            new HashSet<String>());

        assertEquals(2, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(pool, 3)));
    }


    @Test
    public void testFindBestWithConsumerSocketsAndStacking() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = mockStackingProduct(productId, "A test product", "13", "1");

        Pool pool = mockPool(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool, 4)));
    }

    @Test
    public void testFindBestWithConsumerSocketsAndStackingNotEnoughSockets() {
        consumer.setFact("cpu.cpu_socket(s)", "32");

        Product product = mockStackingProduct(productId, "A test product", "13", "1");

        Pool pool = mockPool(product);
        pool.setQuantity(5L);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        // we should consume as many as possible, even if this doesnt fully entitle
        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool, 5)));
    }

    @Test
    public void testFindBestWithStackingOverEntitlesToFullyCover() {
        consumer.setFact("cpu.cpu_socket(s)", "32");

        Product product = mockStackingProduct(productId, "A test product", "13", "3");

        Pool pool = mockPool(product);
        pool.setQuantity(15L);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool, 11)));
    }

    @Test
    public void testFindBestWithStackingWontCrossStackIds() {
        consumer.setFact("cpu.cpu_socket(s)", "32");

        Product product = mockStackingProduct(productId, "A test product", "13", "3");

        Pool pool = mockPool(product);
        pool.setQuantity(10L);

        Product product2 = mockStackingProduct(productId, "A test product 2", "14", "3");

        Pool pool2 = mockPool(product2);
        pool2.setQuantity(10L);


        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        pools.add(pool2);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool, 10)));
    }

    @Test
    public void testFindBestWithStackingSelectsStackThatBestCoversSockets() {
        consumer.setFact("cpu.cpu_socket(s)", "3");

        Product product = mockStackingProduct(productId, "A test product", "13", "1");

        Pool pool = mockPool(product);
        pool.setQuantity(2L);

        Product product2 = mockStackingProduct(productId, "A test product 2", "14", "1");

        Pool pool2 = mockPool(product2);
        pool2.setQuantity(4L);


        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        pools.add(pool2);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 3)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

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
                .selectBestPools(consumer, new String[]{ productId },
                    pools, compliance, null, new HashSet<String>());
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
                .selectBestPools(consumer, new String[]{ productId },
                    pools, compliance, null, new HashSet<String>());
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2 }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool3, 1)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer, new String[]{
            productId1, productId2, productId3 }, pools, compliance, null,
            new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId1 }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
    }

    @Test
    public void testFindBestPrefersToFullyCoverSockets() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        String productIdA = "A";
        String productId1 = "1";
        String productId2 = "2";

        Product productA = new Product(productIdA, "A test product");

        Product product1 = new Product(productId1, "test product 1");
        product1.setAttribute("sockets", "1");

        Product product2 = new Product(productId2, "test product 2");
        product2.setAttribute("sockets", "4");
        product2.setAttribute("multi-entitlement", "yes");
        product2.setAttribute("stacking_id", "1");

        Pool pool1 = mockPool(product1);
        pool1.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        Pool pool2 = mockPool(product2);
        pool2.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        when(this.prodAdapter.getProductById(productIdA)).thenReturn(productA);
        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productIdA }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 2)));
    }

    @Test
    public void testFindBestPrefersToFullyCoverSocketsNoStacking() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        String productIdA = "A";
        String productId1 = "1";
        String productId2 = "2";

        Product productA = new Product(productIdA, "A test product");

        Product product1 = new Product(productId1, "test product 1");
        product1.setAttribute("sockets", "2");

        Product product2 = new Product(productId2, "test product 2");
        product2.setAttribute("sockets", "8");

        Pool pool1 = mockPool(product1);
        pool1.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        Pool pool2 = mockPool(product2);
        pool2.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        when(this.prodAdapter.getProductById(productIdA)).thenReturn(productA);
        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productIdA }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
    }

    @Test
    public void testFindBestTreatsZeroSocketsAsInfiniteNoStacking() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        String productIdA = "A";
        String productId1 = "1";
        String productId2 = "2";

        Product productA = new Product(productIdA, "A test product");

        Product product1 = new Product(productId1, "test product 1");
        product1.setAttribute("sockets", "7");

        Product product2 = new Product(productId2, "test product 2");
        product2.setAttribute("sockets", "0");

        Pool pool1 = mockPool(product1);
        pool1.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        Pool pool2 = mockPool(product2);
        pool2.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        when(this.prodAdapter.getProductById(productIdA)).thenReturn(productA);
        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productIdA }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
    }

    @Test
    public void testFindBestTreatsZeroPoolSocketsAsInfinite() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        String productIdA = "A";
        String productId1 = "1";
        String productId2 = "2";

        Product productA = new Product(productIdA, "A test product");

        Product product1 = new Product(productId1, "test product 1");
        product1.setAttribute("sockets", "0");

        Product product2 = new Product(productId2, "test product 2");
        product2.setAttribute("sockets", "4");
        product2.setAttribute("multi-entitlement", "yes");
        product2.setAttribute("stacking_id", "1");

        Pool pool1 = mockPool(product1);
        pool1.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        Pool pool2 = mockPool(product2);
        pool2.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        when(this.prodAdapter.getProductById(productIdA)).thenReturn(productA);
        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        // Adding these in reverse to not bias towards the first
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool2);
        pools.add(pool1);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productIdA }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
    }

    @Test
    public void testFindBestTreatsUndefinedPoolSocketsAsInfinite() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        String productIdA = "A";
        String productId1 = "1";
        String productId2 = "2";

        Product productA = new Product(productIdA, "A test product");

        Product product1 = new Product(productId1, "test product 1");

        Product product2 = new Product(productId2, "test product 2");
        product2.setAttribute("sockets", "4");
        product2.setAttribute("multi-entitlement", "yes");
        product2.setAttribute("stacking_id", "1");

        Pool pool1 = mockPool(product1);
        pool1.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        Pool pool2 = mockPool(product2);
        pool2.addProvidedProduct(new ProvidedProduct(productA.getId(), productA.getName()));

        when(this.prodAdapter.getProductById(productIdA)).thenReturn(productA);
        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);

        // Adding these in reverse to not bias towards the first
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool2);
        pools.add(pool1);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productIdA }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
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

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId1 }, pools, compliance, null, new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
    }

    // Trying to select best pools for a product that we're already entitled to will
    // just ignore that attempt
    @Test(expected = RuleExecutionException.class)
    public void testFindBestWillNotEntitleACompliantProduct() {
        String productId1 = "A";

        Product product1 = new Product(productId1, "A test product");

        Pool pool1 = TestUtil.createPool(owner, product1);
        pool1.setId("DEAD-BEEF");

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);

        Map<String, Set<Entitlement>> fakeCompliantProducts =
            new HashMap<String, Set<Entitlement>>();
        fakeCompliantProducts.put(productId1, null);

        when(compliance.getCompliantProducts()).thenReturn(fakeCompliantProducts);

        // will raise the RuleExecutionException, for 0 pools
        enforcer.selectBestPools(consumer, new String[]{ productId1 },
            pools, compliance, null, new HashSet<String>());
    }

    // With two pools available, selectBestPools will give us the pool that doesn't
    // provide an entitlement for an already entitled product
    @Test
    public void testFindBestWillChoosePoolThatDoesntIncludeCompliantProduct() {
        String productId1 = "A";
        String productId2 = "B";
        String productId3 = "C";

        Product product1 = new Product(productId1, "A test product");
        Product product2 = new Product(productId2, "A test product 2");
        Product product3 = new Product(productId3, "A test product 3");

        Pool pool1 = TestUtil.createPool(owner, product2);
        pool1.setId("DEAD-BEEF");
        pool1.addProvidedProduct(new ProvidedProduct(product1.getId(), product1.getName()));
        pool1.addProvidedProduct(new ProvidedProduct(product3.getId(), product3.getName()));

        Pool pool2 = TestUtil.createPool(owner, product2);
        pool2.setId("DEAD-BEEF2");

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId2)).thenReturn(product2);
        when(this.prodAdapter.getProductById(productId3)).thenReturn(product3);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);
        pools.add(pool2);

        Map<String, Set<Entitlement>> fakeCompliantProducts =
            new HashMap<String, Set<Entitlement>>();
        fakeCompliantProducts.put(productId1, null);

        when(compliance.getCompliantProducts()).thenReturn(fakeCompliantProducts);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId2, productId3 }, pools, compliance, null,
            new HashSet<String>());

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));

    }

    @Test
    public void testFindBestWillCompleteAPartialStackFromTheSameId() {
        consumer.setFact("cpu.cpu_socket(s)", "8");

        String productId1 = "A";
        String productId3 = "C";

        Product product1 = mockStackingProduct(productId1, "Test Stack product", "1", "2");
        Product product3 = mockProduct(productId3, "Test Provided product");

        Pool pool1 = mockPool(product1);
        pool1.setId("DEAD-BEEF");
        pool1.addProvidedProduct(new ProvidedProduct(product3.getId(), product3.getName()));

        when(this.prodAdapter.getProductById(productId1)).thenReturn(product1);
        when(this.prodAdapter.getProductById(productId3)).thenReturn(product3);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool1);

        Entitlement fakeEntitlement = mock(Entitlement.class);
        when(fakeEntitlement.getPool()).thenReturn(pool1);
        when(fakeEntitlement.getQuantity()).thenReturn(2);

        Map<String, Set<Entitlement>> fakePartial =
            new HashMap<String, Set<Entitlement>>();
        Set<Entitlement> entitlementSet = new HashSet<Entitlement>();
        entitlementSet.add(fakeEntitlement);
        fakePartial.put("1", entitlementSet);

        when(compliance.getPartialStacks()).thenReturn(fakePartial);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId3 }, pools, compliance, null, new HashSet<String>());
        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 2)));
    }

    @Test
    public void testFindBestWillCompleteAPartialStackFromTheSameIdOtherStackAvailable() {
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
        pools.add(pool1);
        pools.add(pool2);

        Entitlement fakeEntitlement = mock(Entitlement.class);
        when(fakeEntitlement.getPool()).thenReturn(pool1);
        when(fakeEntitlement.getQuantity()).thenReturn(2);

        Map<String, Set<Entitlement>> fakePartial =
            new HashMap<String, Set<Entitlement>>();
        Set<Entitlement> entitlementSet = new HashSet<Entitlement>();
        entitlementSet.add(fakeEntitlement);
        fakePartial.put("1", entitlementSet);

        when(compliance.getPartialStacks()).thenReturn(fakePartial);

        List<PoolQuantity> bestPools = enforcer.selectBestPools(consumer,
            new String[]{ productId2, productId3 }, pools, compliance, null,
            new HashSet<String>());
        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 2)));
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

        Entitlement fakeEntitlement = mock(Entitlement.class);
        when(fakeEntitlement.getPool()).thenReturn(pool1);
        when(fakeEntitlement.getQuantity()).thenReturn(2);

        Map<String, Set<Entitlement>> fakePartial =
            new HashMap<String, Set<Entitlement>>();
        Set<Entitlement> entitlementSet = new HashSet<Entitlement>();
        entitlementSet.add(fakeEntitlement);
        fakePartial.put("1", entitlementSet);

        when(compliance.getPartialStacks()).thenReturn(fakePartial);

        enforcer.selectBestPools(consumer, new String[]{ productId2, productId3 },
            pools, compliance, null, new HashSet<String>());
    }

    private Product mockProductSockets(String pid, String productName, String sockets) {
        Product product = new Product(pid, productName);
        product.setAttribute("sockets", sockets);
        product.setAttribute("multi-entitlement", "no");
        when(this.prodAdapter.getProductById(pid)).thenReturn(product);
        return product;
    }

    private Pool setupUserRestrictedPool() {
        Product product = new Product(productId, "A user restricted product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setRestrictedToUsername("bob");
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    private Pool setupHostRestrictedPool(Consumer parent) {
        Product product = new Product(productId, "A host restricted product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.addAttribute(new PoolAttribute("virt_only", "true"));
        pool.addAttribute(new PoolAttribute("requires_host", parent.getUuid()));
        pool.setId("fakeid" + TestUtil.randomInt());
        Entitlement e = new Entitlement(pool, parent, new Date(), new Date(),
            1);

        pool.setSourceEntitlement(e);
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    private Pool setupVirtLimitPool() {
        Product product = new Product(productId, "A virt_limit product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.addAttribute(new PoolAttribute("virt_limit", "10"));
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

}
