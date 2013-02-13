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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.Subscription;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.AttributeHelper;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.policy.js.entitlement.ManifestEntitlementRules;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.Util;
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
    private AttributeHelper attrHelper;

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
        enforcer = new EntitlementRules(new DateSourceImpl(), jsRules,
            productCache, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK), config, consumerCurator);

        owner = new Owner();
        consumer = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));

        attrHelper = new AttributeHelper();

        poolRules = new PoolRules(poolManagerMock, productCache, config);
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
            new JsRunnerProvider(rulesCurator).get(),
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
    public void consumerHavingLessRamThanProductShouldNotGenerateWarning() {
        // Fact specified in kb
        Pool pool = setupArchTest("ram", "4", "memory.memtotal", "2000000");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void consumerHavingEqualRamAsProductShouldNotGenerateWarning() {
        // Fact specified in kb
        Pool pool = setupArchTest("ram", "2", "memory.memtotal", "2000000");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void consumerRamIsRoundedToNearestGbAndShouldNotGenerateWarning() {
        // Fact specified in kb - actual value of 2 GiB in kb.
        Pool pool = setupArchTest("ram", "2", "memory.memtotal", "2097152");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1)
            .getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void consumerHavingMoreRamThanProductGeneratesWarning() {
        Pool pool = setupArchTest("ram", "2", "memory.memtotal", "4000000");

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
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
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
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
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
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
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
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
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
            new JsRunnerProvider(rulesCurator).get(),
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
            new JsRunnerProvider(rulesCurator).get(),
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
            fail("Create pools should not have thrown an exception on bad value for " +
                 "virt_limit. " + e.getMessage());
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
            new JsRunnerProvider(rulesCurator).get(),
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
//        Entitlement e = new Entitlement(pool, parent, new Date(), new Date(),
//            1);
//
////        pool.setSourceEntitlement(e);
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
