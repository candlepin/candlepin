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
package org.candlepin.policy.js.compliance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventSink;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsContext;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.Provider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;



/**
 * ComplianceTest
 */
public class ComplianceRulesTest {
    private Owner owner;
    private ComplianceRules compliance;

    private Owner PRODUCT_OWNER = new Owner("Test Corporation");
    private Product PRODUCT_1 = new Product("p1", "product1");
    private Product PRODUCT_2 = new Product("p2", "product2");
    private Product PRODUCT_3 = new Product("p3", "product3");
    private static final String STACK_ID_1 = "my-stack-1";
    private static final String STACK_ID_2 = "my-stack-2";

    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private EntitlementCurator entCurator;
    @Mock private RulesCurator rulesCuratorMock;
    @Mock private EventSink eventSink;
    @Mock private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock private JsRunnerRequestCache cache;
    @Mock private ProductCurator productCurator;
    @Mock private EnvironmentCurator environmentCurator;

    private ModelTranslator translator;
    private I18n i18n;
    private JsRunnerProvider provider;
    private Consumer consumer;

    private Map<String, String> activeGuestAttrs;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        translator = new StandardTranslator(consumerTypeCurator, environmentCurator, mockOwnerCurator);

        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale, I18nFactory.FALLBACK);
        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        when(cacheProvider.get()).thenReturn(cache);
        provider = new JsRunnerProvider(rulesCuratorMock, cacheProvider);
        compliance = new ComplianceRules(provider.get(), entCurator, new StatusReasonMessageGenerator(i18n),
            eventSink, consumerCurator, consumerTypeCurator,
            new RulesObjectMapper(new ProductCachedSerializationModule(productCurator)), translator);

        owner = new Owner("test");
        owner.setId(TestUtil.randomString());
        when(mockOwnerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);
        activeGuestAttrs = new HashMap<>();
        activeGuestAttrs.put("virtWhoType", "libvirt");
        activeGuestAttrs.put("active", "1");

        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        consumer = new Consumer("test consumer", "test user", owner, ctype);
    }

    /*
     * Make sure additive properties coming back from the javascript do not break when
     * we deserialize.
     */
    @Test
    public void additivePropertiesCanStillDeserialize() {
        JsRunner mockRunner = mock(JsRunner.class);
        compliance = new ComplianceRules(mockRunner, entCurator, new StatusReasonMessageGenerator(i18n),
            eventSink, consumerCurator, consumerTypeCurator,
            new RulesObjectMapper(new ProductCachedSerializationModule(productCurator)), translator);

        when(mockRunner.runJsFunction(any(Class.class), eq("get_status"),
            any(JsContext.class))).thenReturn("{\"unknown\": \"thing\"}");
        Consumer c = mockConsumerWithTwoProductsAndNoEntitlements();

        // Nothing to assert here, we just need to see this return without error.
        compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
    }

    private Consumer mockConsumer(Product ... installedProducts) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype-" + TestUtil.randomInt());

        Consumer consumer = new Consumer();
        consumer.setType(ctype);

        when(this.consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(this.consumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

        for (Product product : installedProducts) {
            consumer.addInstalledProduct(new ConsumerInstalledProduct(product.getId(), product.getName()));
        }

        consumer.setFact("cpu.cpu_socket(s)", "8"); // 8 socket machine
        return consumer;
    }

    private Entitlement mockEntitlement(Consumer consumer, Product product, Product ... providedProducts) {
        // Make the end date relative to now so it won't outrun it over time.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 50);

        return mockEntitlement(
            consumer,
            product,
            TestUtil.createDate(2000, 1, 1),
            cal.getTime(),
            providedProducts
        );
    }

    private Entitlement mockEntitlement(Consumer consumer, Product product, Date start, Date end,
        Product ... providedProducts) {

        Set<Product> ppset = new HashSet<>();
        for (Product pp : providedProducts) {
            ppset.add(pp);
        }
        product.setProvidedProducts(ppset);

        Pool pool = new Pool(
            owner,
            product,
            ppset,
            new Long(1000),
            start,
            end,
            "1000",
            "1000",
            "1000"
        );

        pool.setId("pool_" + TestUtil.randomInt());
        pool.setUpdated(new Date());
        pool.setCreated(new Date());
        when(productCurator.getPoolProvidedProductsCached(pool.getId()))
            .thenReturn(pool.getProvidedProducts());
        Entitlement e = new Entitlement(pool, consumer, owner, 1);
        e.setId("ent_" + TestUtil.randomInt());
        e.setUpdated(new Date());
        e.setCreated(new Date());

        return e;
    }

    private Entitlement mockBaseStackedEntitlement(Consumer consumer, String stackId,
        Product product, Product ... providedProducts) {

        Entitlement e = mockEntitlement(consumer, product, providedProducts);

        Random gen = new Random();
        int id = gen.nextInt(Integer.MAX_VALUE);
        e.setId(String.valueOf(id));

        // Setup the attributes for stacking:
        Pool pool = e.getPool();
        pool.getProduct().setAttribute(Product.Attributes.STACKING_ID, stackId);

        return e;
    }

    private Entitlement mockStackedEntitlement(Consumer consumer, String stackId,
        Product product, Product ... providedProducts) {

        Entitlement ent = this.mockBaseStackedEntitlement(consumer, stackId, product, providedProducts);
        ent.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "2");
        return ent;
    }

    private Entitlement mockInstanceEntitlement(Consumer consumer, String stackId,
        String instanceMultiplier, Product product, Product ... providedProducts) {
        Entitlement ent = this.mockBaseStackedEntitlement(consumer, stackId, product, providedProducts);

        Pool pool = ent.getPool();
        pool.getProduct().setAttribute(Product.Attributes.SOCKETS, "2");
        pool.getProduct().setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, instanceMultiplier);

        return ent;
    }

    private Entitlement mockHostRestrictedEntitlement(Consumer consumer, String stackId,
        Product product, Product ... providedProducts) {
        Entitlement ent = this.mockBaseStackedEntitlement(consumer, stackId, product, providedProducts);

        Pool pool = ent.getPool();
        pool.setAttribute(Pool.Attributes.REQUIRES_HOST, "SOMEUUID");
        pool.getProduct().setAttribute(Product.Attributes.SOCKETS, "2");
        pool.getProduct().setAttribute(Product.Attributes.VCPU, "1");

        return ent;
    }

    private Entitlement mockNonStackedHostRestrictedEntitlement(Consumer consumer, String stackId,
        Product product, Product ... providedProducts) {

        Entitlement ent = this.mockEntitlement(consumer, product, providedProducts);

        Pool pool = ent.getPool();
        pool.setAttribute(Pool.Attributes.REQUIRES_HOST, "SOMEUUID");
        pool.getProduct().setAttribute(Product.Attributes.SOCKETS, "2");
        pool.getProduct().setAttribute(Product.Attributes.VCPU, "1");

        return ent;
    }

    private Consumer mockConsumerWithTwoProductsAndNoEntitlements() {
        return mockConsumer(PRODUCT_1, PRODUCT_2);
    }

    private Consumer mockFullyEntitledConsumer() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"), PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);
        return c;
    }

    @Test
    public void noEntitlements() {
        Consumer c = mockConsumerWithTwoProductsAndNoEntitlements();
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(2, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }

    @Test
    public void entitledProducts() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"), PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(1, status.getNonCompliantProducts().size());
        assertTrue(status.getNonCompliantProducts().contains(PRODUCT_2.getId()));

        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));

        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }

    @Test
    public void fullyEntitled() {
        Consumer c = mockFullyEntitledConsumer();

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());

        // Our one entitlement should cover both of these:
        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }

    @Test
    public void testArchitectureMismatch() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "PPC64");
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"), PRODUCT_2));
        ents.get(1).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        // Our one entitlement should not cover both of these:
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertEquals("partial", status.getStatus());
    }

    @Test
    public void testMultiArchitectureMatch() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "PPC64,x86_64");
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"), PRODUCT_2));
        ents.get(1).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        // Our one entitlement should not cover both of these:
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertEquals("valid", status.getStatus());
    }

    /*
     * Test an installed product which has a normal non-stacked entitlement, but to a
     * product which does not cover sufficient sockets for the system.
     */
    @Test
    public void regularEntButLackingSocketCoverage() {
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "4");
        ents.get(0).setQuantity(1000); // quantity makes no difference outside stacking
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
    }

    /*
     * Test an installed product with two normal non-stacked entitlements, one which
     * provides enough sockets, and one which does not. Note that these entitlements
     * are not stacked together.
     */
    @Test
    public void regularEntsOneLackingSocketCoverage() {
        // Consumer with 8 sockets:
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<>();

        // One entitlement that only provides four sockets:
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "4");

        // One entitlement that provides ten sockets:
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(1).getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "10");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
    }

    /*
     * Test an installed product with two normal non-stacked entitlements, both of which
     * do not provide enough sockets for the system. Note that these entitlements
     * are not stacked together.
     */
    @Test
    public void regularEntsBothLackingSocketCoverage() {
        // Consumer with 8 sockets:
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<>();

        // One entitlement that only provides four sockets:
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "4");

        // One entitlement that provides 6 sockets:
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(1).getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "6");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
    }

    /*
     * Test an installed product which is fully covered by a normal entitlement, but also
     * by a partial stack. In this case it should show up as a compliant product,
     * a partial stack, but not a partially compliant product.
     */
    @Test
    public void regularEntPlusPartialStack() {
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getPartialStacks().keySet().contains(STACK_ID_1));
    }

    @Test
    public void regularEntPlusArchMismatchStack() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        ents.get(1).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "PPC64");
        ents.get(2).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        ents.get(3).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "PPC64");
        ents.get(4).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "PPC64");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertEquals("partial", status.getStatus());
    }

    public void testComplianceDoesNotEnforceSocketsWhenAttributeNotSet() {
        // Consumer with 8 sockets:
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));

        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
    }

    @Test
    public void testComplianceCountsZeroPoolSocketsAsNotSet() {
        // Consumer with 8 sockets:
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "0");

        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
    }

    @Test
    public void testComplianceCountsUndefinedPoolSocketsAsInfinite() {
        // Consumer with 8 sockets:
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<>();

        // One entitlement that only provides four sockets:
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));

        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
    }

    // Test a fully stacked scenario:
    @Test
    public void compliantStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    @Test
    public void testArchMismatchStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        ents.get(1).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "PPC64");
        ents.get(2).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        ents.get(3).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertEquals(4, status.getPartiallyCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(4, status.getPartiallyCompliantProducts().get(PRODUCT_2.getId()).size());
        assertEquals(1, status.getPartialStacks().size());
        assertTrue(status.getPartialStacks().keySet().contains(STACK_ID_1));
        assertEquals("partial", status.getStatus());
    }

    // Test a partially stacked scenario:
    @Test
    public void partiallyCompliantStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();

        // Three entitlements, 2 sockets each, is not enough for our 8 sockets:
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());

        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(3, status.getPartiallyCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(3, status.getPartiallyCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    // Test having more stacked entitlements than we need:
    @Test
    public void overCompliantStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(8, status.getCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(8, status.getCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    @Test
    public void notInstalledButPartiallyStacked() {
        // TODO: spoke to PM, this is considered invalid even though the product is
        // not technically installed. (yet)
        Consumer c = mockConsumer(); // nothing installed
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getPartialStacks().size());

        assertTrue(status.getPartialStacks().keySet().contains(STACK_ID_1));
        assertEquals("partial", status.getStatus());
    }

    @Test
    public void partialStackProvidingDiffProducts() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2, PRODUCT_3);
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_3));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());

        assertEquals(3, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(2, status.getPartiallyCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(1, status.getPartiallyCompliantProducts().get(PRODUCT_2.getId()).size());
        assertEquals(1, status.getPartiallyCompliantProducts().get(PRODUCT_3.getId()).size());

        assertEquals(1, status.getPartialStacks().size());
    }

    @Test
    public void fullStackProvidingDiffProducts() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2, PRODUCT_3);
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_3));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"), PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"), PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(3, status.getCompliantProducts().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertEquals(2, status.getCompliantProducts().get(PRODUCT_1.getId()).size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
        assertEquals(3, status.getCompliantProducts().get(PRODUCT_2.getId()).size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(1, status.getCompliantProducts().get(PRODUCT_3.getId()).size());

        assertEquals(0, status.getPartialStacks().size());
    }

    @Test
    public void partialStackAndFullStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();

        // Partial stack:
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        // Full stack providing only product 1:
        ents.add(mockStackedEntitlement(
            c, STACK_ID_2, TestUtil.createProduct("Awesome Product 2"), PRODUCT_1
        ));
        ents.add(mockStackedEntitlement(
            c, STACK_ID_2, TestUtil.createProduct("Awesome Product 2"), PRODUCT_1
        ));
        ents.add(mockStackedEntitlement(
            c, STACK_ID_2, TestUtil.createProduct("Awesome Product 2"), PRODUCT_1
        ));
        ents.add(mockStackedEntitlement(
            c, STACK_ID_2, TestUtil.createProduct("Awesome Product 2"), PRODUCT_1
        ));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));

        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(1, status.getPartialStacks().size());
    }

    @Test
    public void compliantUntilDateIsNullWhenNoInstalledProductsAndNoEntitlements() {
        Consumer consumer = mockConsumer();
        ComplianceStatus status = compliance.getStatus(consumer, new Date());
        assertNull(status.getCompliantUntil());
    }

    @Test
    public void compliantUntilDateIsOnDateWhenInstalledProductsButNoEntitlements() {
        Consumer consumer = mockConsumer(TestUtil.createProduct("Only One Installed Prod"));
        Date expectedOnDate = TestUtil.createDate(9999, 4, 12);
        ComplianceStatus status = compliance.getStatus(consumer, expectedOnDate);
        assertNull(status.getCompliantUntil());
    }

    @Test
    public void compliantUntilDateIsDateOfFirstEntitlementToExpireCausingNonCompliant() {
        Consumer consumer = mockConsumer(PRODUCT_1, PRODUCT_2);

        Date start = TestUtil.createDate(2005, 6, 12);

        Entitlement ent1 = mockEntitlement(
            consumer,
            TestUtil.createProduct("Provides Product 1 For Short Period"),
            start,
            TestUtil.createDate(2005, 6, 22),
            PRODUCT_1
        );

        Entitlement ent2 = mockEntitlement(
            consumer,
            TestUtil.createProduct("Provides Product 1 past Ent3"),
            TestUtil.createDate(2005, 6, 20),
            TestUtil.createDate(2005, 7, 28),
            PRODUCT_1
        );

        Entitlement ent3 = mockEntitlement(
            consumer,
            TestUtil.createProduct("Provides Product 2 Past Ent1"),
            start,
            TestUtil.createDate(2005, 7, 18),
            PRODUCT_2
        );

        mockEntCurator(consumer, Arrays.asList(ent1, ent2, ent3));

        Date statusDate = TestUtil.createDate(2005, 6, 14);
        Date expectedDate = addSecond(ent3.getEndDate());
        ComplianceStatus status = compliance.getStatus(consumer, statusDate);
        assertEquals(expectedDate, status.getCompliantUntil());
    }

    @Test
    public void compliantUntilDateUsesFutureEntitlements() {
        Consumer consumer = mockConsumer(PRODUCT_1, PRODUCT_2);

        Date start = TestUtil.createDate(2005, 6, 12);

        List<Entitlement> ents = new LinkedList<>();
        int iterations = 5;
        int interval = 1000;
        for (int i = 0; i < interval * iterations; i += interval) {
            ents.add(mockEntitlement(
                consumer,
                TestUtil.createProduct("Provides Product 1 For Short Period"),
                new Date(start.getTime() + i),
                new Date(start.getTime() + i + interval),
                PRODUCT_1
            ));

            ents.add(mockEntitlement(
                consumer,
                TestUtil.createProduct("Provides Product 2 For Short Period"),
                new Date(start.getTime() + i),
                new Date(start.getTime() + i + interval),
                PRODUCT_2
            ));
        }

        mockEntCurator(consumer, ents);

        Date expectedDate = addSecond(new Date(start.getTime() + interval * (iterations - 1) + interval));
        ComplianceStatus status = compliance.getStatus(consumer, start);
        assertEquals("valid", status.getStatus());
        assertEquals(expectedDate, status.getCompliantUntil());
    }

    @Test
    public void compliantUntilReturnsNullIfNoProductsInstalled() {
        Consumer consumer = mockConsumer();

        Date start = TestUtil.createDate(2005, 6, 12);

        List<Entitlement> ents = new LinkedList<>();
        int iterations = 5;
        int interval = 1000;
        for (int i = 0; i < interval * iterations; i += interval) {
            ents.add(mockEntitlement(
                consumer,
                TestUtil.createProduct("Provides Product 1 For Short Period"),
                new Date(start.getTime() + i),
                new Date(start.getTime() + i + interval),
                PRODUCT_1
            ));
            ents.add(mockEntitlement(
                consumer,
                TestUtil.createProduct("Provides Product 2 For Short Period"),
                new Date(start.getTime() + i),
                new Date(start.getTime() + i + interval),
                PRODUCT_2
            ));
        }

        mockEntCurator(consumer, ents);

        Date expectedDate = null;
        ComplianceStatus status = compliance.getStatus(consumer, start);
        assertEquals("valid", status.getStatus());
        assertEquals(expectedDate, status.getCompliantUntil());
    }

    private Date addSecond(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, 1);
        return cal.getTime();
    }

    @Test
    public void compliantUntilDateIsOnDateWhenPartialStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();

        // Partial stack: covers only 4 sockets... consumer has 8.
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        Date expectedOnDate = TestUtil.createDate(2011, 8, 30);
        ComplianceStatus status = compliance.getStatus(c, expectedOnDate);
        assertEquals(1, status.getPartialStacks().size());
        assertNull(status.getCompliantUntil());
    }

    // NOTE: This scenario should NEVER happen since listByConsumerAndDate should
    //       never return dates before the specified date. This test exists to
    //       test the guard clauses in the ComplianceRulesHelper in case it ever happened.
    @Test
    public void expiredEntitlementIsIgnoredWhenCalculatingCompliantUntilDate() {
        Consumer consumer = mockConsumer(PRODUCT_1);

        Date start = TestUtil.createDate(2005, 6, 12);

        Entitlement expired = mockEntitlement(
            consumer,
            TestUtil.createProduct("Provides Product 1 past Ent3"),
            TestUtil.createDate(2005, 5, 20),
            TestUtil.createDate(2005, 6, 2),
            PRODUCT_1
        );

        Entitlement ent = mockEntitlement(
            consumer,
            TestUtil.createProduct("Provides Product 1 For Short Period"),
            start,
            TestUtil.createDate(2005, 6, 22),
            PRODUCT_1
        );

        // Set up entitlements at specific dates.
        mockEntCurator(consumer, Arrays.asList(expired, ent));

        Date expectedDate = addSecond(ent.getEndDate());
        ComplianceStatus status = compliance.getStatus(consumer, start);
        assertEquals(expectedDate, status.getCompliantUntil());
    }

    @Test
    public void statusGreenWhenConsumerHasNoInstalledProducts() {
        Consumer c = mockConsumer();
        List<Entitlement> ents = new LinkedList<>();
        mockEntCurator(c, ents);
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void statusGreenWhenFullyEntitled() {
        Consumer c = mockFullyEntitledConsumer();
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void statusYellowWhenNoNonCompliantAndHasPartiallyCoveredProducts() {
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    @Test
    public void statusRedWhenNonCompliantProductOnly() {
        Consumer c = mockConsumerWithTwoProductsAndNoEntitlements();
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void statusRedWhenNonCompliantProductAndCompliantProduct() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void statusRedWhenNonCompliantProductAndPartialProduct() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void statusRedOneWhenNonCompliantProductAndCompliantAndPartial() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2, PRODUCT_3);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1));
        ents.add(mockEntitlement(c, TestUtil.createProduct("Another Product"), PRODUCT_3));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void stackIsCompliant() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.cpu_socket(s)", "2");

        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"), PRODUCT_1));
        assertTrue(compliance.isStackCompliant(c, STACK_ID_1, ents));
    }

    @Test
    public void stackIsNotCompliant() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.cpu_socket(s)", "4");

        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"), PRODUCT_1));
        ents.add(mockEntitlement(c, TestUtil.createProduct("Another Product"), PRODUCT_3));
        assertFalse(compliance.isStackCompliant(c, STACK_ID_1, ents));
    }

    @Test
    public void entIsCompliantIfSocketsNotSetOnEntPool() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.cpu_socket(s)", "2");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(Arrays.asList(ent));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(cqmock);

        assertTrue(compliance.isEntitlementCompliant(c, ent, new Date()));
    }

    @Test
    public void entIsCompliantWhenSocketsAreCovered() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.cpu_socket(s)", "4");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "4");

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(Arrays.asList(ent));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(cqmock);

        assertTrue(compliance.isEntitlementCompliant(c, ent, new Date()));
    }

    @Test
    public void entIsNotCompliantWhenSocketsAreNotCovered() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.cpu_socket(s)", "8");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "4");

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(Arrays.asList(ent));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(cqmock);

        assertFalse(compliance.isEntitlementCompliant(c, ent, new Date()));
    }

    @Test
    public void entPartialStackNoProductsInstalled() {
        Consumer c = mockConsumer();
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"), PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"), PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"), PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"), PRODUCT_1));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        ents.get(1).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "PPC64");
        ents.get(2).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        ents.get(3).getPool().getProduct().setAttribute(Product.Attributes.ARCHITECTURE, "PPC64");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(1, status.getPartialStacks().size());

        assertTrue(status.getPartialStacks().keySet().contains(STACK_ID_1));
        assertEquals("partial", status.getStatus());
    }

    // Cores with not-stackable entitlement tests
    @Test
    public void productCoveredWhenSingleEntitlementCoversCores() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.core(s)_per_socket", "4");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().getProduct().setAttribute(Product.Attributes.CORES, "32");
        mockEntCurator(c, Arrays.asList(ent));
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productPartiallyCoveredWhenSingleEntitlementDoesNotCoverAllCores() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.core(s)_per_socket", "8");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().getProduct().setAttribute(Product.Attributes.CORES, "4");
        mockEntCurator(c, Arrays.asList(ent));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    // Cores stacking tests
    @Test
    public void productCoveredWhenStackedEntsCoverCores() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("cpu.core(s)_per_socket", "8");

        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().getProduct().setAttribute(Product.Attributes.CORES, "32");

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().getProduct().setAttribute(Product.Attributes.CORES, "32");

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productPartiallyCoveredWhenStackingEntsDoesNotCoverCores() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("cpu.core(s)_per_socket", "8");

        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        // Cores are not covered.
        ent1.getPool().getProduct().setAttribute(Product.Attributes.CORES, "4");

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        // Mock consumer has 8 sockets by default.
        ent2.getPool().getProduct().setAttribute(Product.Attributes.CORES, "1");

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    // Multi-attribute stacking tests.
    @Test
    public void productCoveredWhenStackedEntitlementCoversBothSocketsAndCores() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().getProduct().setAttribute(Product.Attributes.CORES, "32");
        ent1.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "4");

        mockEntCurator(c, Arrays.asList(ent1));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productCoveredWhenTwoStackedEntsCoversBothSocketsAndCores() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().getProduct().setAttribute(Product.Attributes.CORES, "24");
        ent1.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "2");

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().getProduct().setAttribute(Product.Attributes.CORES, "8");
        ent2.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "2");

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productCoveredWhenTwoStackedEntsCoverBothSocketsAndCoresSeperately() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "4");

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().getProduct().setAttribute(Product.Attributes.CORES, "32");

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productPartiallyCoveredWhenTwoStackedEntsCoverOnlyCores() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "2");

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().getProduct().setAttribute(Product.Attributes.CORES, "8");

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    @Test
    public void productPartiallyCoveredWhenStackedEntsCoverSocketsOnly() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "4");

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().getProduct().setAttribute(Product.Attributes.CORES, "4");

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    // RAM stacking tests
    @Test
    public void productCoveredWhenStackedEntsCoverRam() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("memory.memtotal", "8000000"); // 8GB RAM

        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().getProduct().setAttribute(Product.Attributes.RAM, "4");

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().getProduct().setAttribute(Product.Attributes.RAM, "4");

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3.getId()));
    }

    @Test
    public void productPartiallyCoveredWhenStackingEntsDoesNotCoverRam() {
        Consumer c = mockConsumer(PRODUCT_3);
        c.setFact("memory.memtotal", "8000000"); // 8GB

        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        // Cores are not covered.
        ent1.getPool().getProduct().setAttribute(Product.Attributes.RAM, "4");

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        // Mock consumer has 8 sockets by default.
        ent2.getPool().getProduct().setAttribute(Product.Attributes.RAM, "1");

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_3.getId()));
        assertEquals(0, status.getCompliantProducts().size());
    }

    @Test
    public void instanceBasedPhysicalGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(8);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
    }

    @Test
    public void singleSocketInstanceBasedPhysicalGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("cpu.cpu_socket(s)", "1");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "1");
        ents.get(0).setQuantity(2);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
    }

    @Test
    public void instanceBasedPhysicalStackedGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(6);
        ents.get(1).setQuantity(2);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
    }

    @Test
    public void instanceBasedPhysicalYellow() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(7);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        // Should be covered for 6 of 8 sockets, not 7 because the quantity is
        // adjusted for sockets to a multiple of the instance multiplier
        assertEquals(1, status.getReasons().size());
        ComplianceReason reason = status.getReasons().iterator().next();
        assertEquals("SOCKETS", reason.getKey());
        assertEquals("6", reason.getAttributes().get("covered"));
    }

    @Test
    public void instanceBasedVirtualGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("virt.is_guest", "true");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(1);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
    }

    @Test
    public void hostRestrictedVirtualGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("virt.is_guest", "true");
        c.setFact("cpu.core(s)_per_socket", "20");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockHostRestrictedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(1);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
    }

    /*
     * We should not run compliance on host restricted subscriptions,
     * even if they aren't stackable
     */
    @Test
    public void hostNonStackedRestrictedVirtualGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("virt.is_guest", "true");
        c.setFact("cpu.core(s)_per_socket", "20");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockNonStackedHostRestrictedEntitlement(
            c,
            "Awesome Product",
            PRODUCT_1,
            PRODUCT_2
        ));
        ents.get(0).setQuantity(1);
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.VCPU, "1");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));
    }

    @Test
    public void virtLimitSingleEntitlementCovered() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.core(s)_per_socket", "4");
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().getProduct().setAttribute(Product.Attributes.CORES, "32");
        ent.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "8");

        mockEntCurator(c, Arrays.asList(ent));
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
    }

    @Test
    public void virtLimitSingleEntitlementPartial() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.core(s)_per_socket", "4");
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().getProduct().setAttribute(Product.Attributes.CORES, "32");
        ent.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "4");

        mockEntCurator(c, Arrays.asList(ent));
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    @Test
    public void partiallyCompliantVirtLimitStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        }
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());

        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(4, status.getPartiallyCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(4, status.getPartiallyCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    @Test
    public void fullyCompliantVirtLimitStackWithInactiveGuests() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c));
        }
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        }
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    @Test
    public void fullyCompliantVirtLimitStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "8");
        }
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    @Test
    public void fullyCompliantVirtLimitStackVaryingLimits() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "1");
        }
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "5");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    @Test
    public void fullyCompliantVirtLimitStackWithUnlimited() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "1");
        }
        ents.get(0).getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    @Test
    public void fullyCompliantVirtLimitStackAllUnlimited() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        }
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    /*
     * This test should behave more like the scenarios discussed in implementation.
     * A base subscription with a virt limit of 4, and a hypervisor which overrides
     * the unrelated subscriptions virt limit, making it unlimited as well.
     */
    @Test
    public void fullyCompliantOverriddenVirtLimit() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        Entitlement mockServerEntitlement = mockEntitlement(
            c,
            TestUtil.createProduct("Awesome OS server"),
            PRODUCT_1,
            PRODUCT_2
        );
        mockServerEntitlement.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        ents.add(mockServerEntitlement);
        mockEntCurator(c, ents);

        // Before we add the hypervisor, this product shouldn't be compliant
        ComplianceStatus status =
            compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        // Should be partial
        assertEquals("partial", status.getStatus());
        assertEquals(2, status.getPartiallyCompliantProducts().size());

        Entitlement mockHypervisorEntitlement = mockEntitlement(
            c,
            TestUtil.createProduct("Awesome Enterprise Hypervisor"),
            PRODUCT_1,
            PRODUCT_2
        );
        mockHypervisorEntitlement.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        ents.add(mockHypervisorEntitlement);
        mockEntCurator(c, ents);

        // Now that we've added the hypervisor,
        // the base guest_limit of 4 should be overridden
        status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        assertEquals(2, status.getCompliantProducts().get(PRODUCT_1.getId()).size());
        assertEquals(2, status.getCompliantProducts().get(PRODUCT_2.getId()).size());
    }

    @Test
    public void isEntFullyCompliantOverriddenVirtLimit() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        Entitlement mockServerEntitlement = mockEntitlement(c, TestUtil.createProduct("Awesome OS server"),
            PRODUCT_1, PRODUCT_2);
        mockServerEntitlement.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        ents.add(mockServerEntitlement);

        Entitlement mockHypervisorEntitlement = mockEntitlement(
            c,
            TestUtil.createProduct("Awesome Enterprise Hypervisor"),
            PRODUCT_1,
            PRODUCT_2
        );
        mockHypervisorEntitlement.getPool()
            .getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        ents.add(mockHypervisorEntitlement);

        mockEntCurator(c, ents);
        // Now that we've added the hypervisor,
        // the base guest_limit of 4 should be overridden
        assertTrue(compliance.isEntitlementCompliant(c, mockServerEntitlement, new Date()));
    }

    @Test
    public void isEntPartiallyCompliantNonOverriddenVirtLimit() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        Entitlement mockServerEntitlement = mockEntitlement(
            c,
            TestUtil.createProduct("Awesome OS server"),
            PRODUCT_1,
            PRODUCT_2
        );
        mockServerEntitlement.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        ents.add(mockServerEntitlement);

        mockEntCurator(c, ents);

        // The guest limit has not been modified, should not be compliant.
        assertFalse(compliance.isEntitlementCompliant(c, mockServerEntitlement, new Date()));
    }

    @Test
    public void isStackFullyCompliantOverriddenVirtLimit() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<>();

        Entitlement mockServerEntitlement = mockStackedEntitlement(c, "mockServerStack",
            PRODUCT_1, PRODUCT_2);
        mockServerEntitlement.getPool().getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        mockServerEntitlement.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "2");
        mockServerEntitlement.setQuantity(4);
        ents.add(mockServerEntitlement);

        Entitlement mockHypervisorEntitlement = mockEntitlement(
            c,
            TestUtil.createProduct("Awesome Enterprise Hypervisor"),
            PRODUCT_1,
            PRODUCT_2
        );
        mockHypervisorEntitlement.getPool()
            .getProduct().setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        ents.add(mockHypervisorEntitlement);

        // Now that we've added the hypervisor,
        // the base guest_limit of 4 should be overridden
        assertTrue(compliance.isStackCompliant(c, "mockServerStack", ents));
    }

    @Test
    public void virtualSingleEntOnlyUsesVcpuAndRam() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("virt.is_guest", "true");
        c.setFact("cpu.core(s)_per_socket", "8");
        c.setFact("cpu.cpu_socket(s)", "10");
        c.setFact("memory.memtotal", "8000000"); // 8GB RAM

        List<Entitlement> ents = new LinkedList<>();
        Entitlement ent = mockEntitlement(c, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1);
        ent.getPool().getProduct().setAttribute(Product.Attributes.CORES, "1");
        ent.getPool().getProduct().setAttribute(Product.Attributes.RAM, "1");
        ent.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "1");
        ent.getPool().getProduct().setAttribute(Product.Attributes.VCPU, "1");
        ents.add(ent);
        ents.get(0).setQuantity(1);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));

        // We should be partial because of ram and vcpus, nothing else
        assertEquals(2, status.getReasons().size());
        List<String> reasonKeys = new LinkedList<>();
        for (ComplianceReason r : status.getReasons()) {
            reasonKeys.add(r.getKey());
        }
        assertTrue(reasonKeys.contains("RAM"));
        assertTrue(reasonKeys.contains("VCPU"));
    }

    @Test
    public void virtualStackedOnlyUsesVcpuAndRam() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("virt.is_guest", "true");
        c.setFact("cpu.core(s)_per_socket", "8");
        c.setFact("cpu.cpu_socket(s)", "10");
        c.setFact("memory.memtotal", "8000000"); // 8GB RAM

        List<Entitlement> ents = new LinkedList<>();
        Entitlement ent = mockBaseStackedEntitlement(c, STACK_ID_1, TestUtil.createProduct("Awesome Product"),
            PRODUCT_1, PRODUCT_2);
        ent.getPool().getProduct().setAttribute(Product.Attributes.CORES, "1");
        ent.getPool().getProduct().setAttribute(Product.Attributes.RAM, "1");
        ent.getPool().getProduct().setAttribute(Product.Attributes.SOCKETS, "1");
        ent.getPool().getProduct().setAttribute(Product.Attributes.VCPU, "1");
        ents.add(ent);
        ents.get(0).setQuantity(1);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2.getId()));

        // We should be partial because of ram and vcpus, nothing else
        assertEquals(2, status.getReasons().size());
        List<String> reasonKeys = new LinkedList<>();
        for (ComplianceReason r : status.getReasons()) {
            reasonKeys.add(r.getKey());
        }
        assertTrue(reasonKeys.contains("RAM"));
        assertTrue(reasonKeys.contains("VCPU"));
    }

    @Test
    public void ensureConsumerEntitlementStatusIsUpdatedWhenChanged() {
        Consumer c = mockFullyEntitledConsumer();
        ComplianceStatus originalStatus = compliance.getStatus(c);
        assertEquals("valid", originalStatus.getStatus());

        verify(consumerCurator).update(eq(c), eq(false));
        String pid = "testinstalledprod";
        c.addInstalledProduct(new ConsumerInstalledProduct(pid, pid));
        ComplianceStatus updated = compliance.getStatus(c);
        assertNotEquals(originalStatus.getStatus(), updated.getStatus());
        assertEquals(c.getEntitlementStatus(), updated.getStatus());
    }

    @Test
    public void ensureConsumerComplianceStatusHashIsUpdatedWhenComplianceChanges() {
        Consumer c = mockFullyEntitledConsumer();
        ComplianceStatus originalStatus = compliance.getStatus(c);
        assertEquals("valid", originalStatus.getStatus());

        String initialHash = c.getComplianceStatusHash();
        assertNotNull(initialHash);
        assertFalse(initialHash.isEmpty());

        verify(consumerCurator).update(eq(c), eq(false));
        String pid = "testinstalledprod";
        c.addInstalledProduct(new ConsumerInstalledProduct(pid, pid));
        ComplianceStatus updated = compliance.getStatus(c);
        assertNotEquals(originalStatus.getStatus(), updated.getStatus());

        String updatedHash = c.getComplianceStatusHash();
        assertNotNull(updatedHash);
        assertFalse(updatedHash.isEmpty());
        assertNotEquals(initialHash, updatedHash);
    }

    @Test
    public void unmappedGuestEntitlementYellow() {
        Consumer c = mockConsumer(PRODUCT_1);

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        ent.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "true");
        ent.getPool().setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        mockEntCurator(c, Arrays.asList(ent));
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1.getId()));
        assertEquals(1, status.getReasons().size());
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    private void mockEntCurator(Consumer c, List<Entitlement> ents) {
        CandlepinQuery cqmock = mock(CandlepinQuery.class);

        c.setEntitlements(new HashSet<>(ents));

        when(cqmock.list()).thenReturn(ents);
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(cqmock);
    }

    /*
     * This test demonstrates that a syspurpose role mismatch should not affect a consumer's
     * entitlement compliance status.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testRoleMismatchShouldNotAffectEntitlementComplianceStatus() {
        Product engineeringProduct = new Product();
        engineeringProduct.setId("compliant-69");

        // Consumer specified syspurpose attributes:
        Consumer myconsumer = mockConsumer(engineeringProduct);
        myconsumer.setRole("my_role");

        List<Entitlement> ents = new LinkedList<>();
        Product marketingProduct = TestUtil.createProduct("Awesome Product");
        marketingProduct.setAttribute(Product.Attributes.ROLES, "provided_role");
        ents.add(mockEntitlement(myconsumer, marketingProduct, engineeringProduct));
        mockEntCurator(myconsumer, ents);

        ComplianceStatus status = compliance.getStatus(myconsumer, TestUtil.createDate(2011, 8, 30));

        assertEquals(status.getStatus(), "valid");
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(engineeringProduct.getId()));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }

    /*
     * This test demonstrates that a syspurpose addon mismatch should not affect a consumer's
     * entitlement compliance status.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testAddonMismatchShouldNotAffectEntitlementComplianceStatus() {
        Product engineeringProduct = new Product();
        engineeringProduct.setId("compliant-69");

        // Consumer specified syspurpose attributes:
        Consumer myconsumer = mockConsumer(engineeringProduct);
        Set<String> addons = new HashSet<>();
        addons.add("my_addon");
        myconsumer.setAddOns(addons);

        List<Entitlement> ents = new LinkedList<>();
        Product marketingProduct = TestUtil.createProduct("Awesome Product");
        marketingProduct.setAttribute(Product.Attributes.ADDONS, "provided_addon");
        ents.add(mockEntitlement(myconsumer, marketingProduct, engineeringProduct));
        mockEntCurator(myconsumer, ents);

        ComplianceStatus status = compliance.getStatus(myconsumer, TestUtil.createDate(2011, 8, 30));

        assertEquals(status.getStatus(), "valid");
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(engineeringProduct.getId()));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }
}
