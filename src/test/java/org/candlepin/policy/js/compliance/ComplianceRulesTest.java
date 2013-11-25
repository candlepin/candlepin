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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsContext;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;



/**
 * ComplianceTest
 */
public class ComplianceRulesTest {
    private Owner owner;
    private ComplianceRules compliance;

    private static final String PRODUCT_1 = "product1";
    private static final String PRODUCT_2 = "product2";
    private static final String PRODUCT_3 = "product3";
    private static final String STACK_ID_1 = "my-stack-1";
    private static final String STACK_ID_2 = "my-stack-2";

    @Mock private EntitlementCurator entCurator;
    @Mock private RulesCurator rulesCuratorMock;
    private I18n i18n;
    private JsRunnerProvider provider;

    private Map<String, String> activeGuestAttrs;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);
        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        provider = new JsRunnerProvider(rulesCuratorMock);
        compliance = new ComplianceRules(provider.get(),
            entCurator, new StatusReasonMessageGenerator(i18n));
        owner = new Owner("test");
        activeGuestAttrs = new HashMap<String, String>();
        activeGuestAttrs.put("virtWhoType", "libvirt");
        activeGuestAttrs.put("active", "1");
    }

    /*
     * Make sure additive properties coming back from the javascript do not break when
     * we deserialize.
     */
    @Test
    public void additivePropertiesCanStillDeserialize() {
        JsRunner mockRunner = mock(JsRunner.class);
        compliance = new ComplianceRules(mockRunner,
            entCurator, new StatusReasonMessageGenerator(i18n));
        when(mockRunner.runJsFunction(any(Class.class), eq("get_status"),
            any(JsContext.class))).thenReturn("{\"unknown\": \"thing\"}");
        Consumer c = mockConsumerWithTwoProductsAndNoEntitlements();

        // Nothing to assert here, we just need to see this return without error.
        compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
    }

    private Consumer mockConsumer(String ... installedProducts) {
        Consumer c = new Consumer();
        c.setType(new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));
        for (String pid : installedProducts) {
            c.addInstalledProduct(new ConsumerInstalledProduct(pid, pid));
        }
        c.setFact("cpu.cpu_socket(s)", "8"); // 8 socket machine
        return c;
    }

    private Entitlement mockEntitlement(Consumer consumer, String productId,
        String ... providedProductIds) {
        return mockEntitlement(consumer, productId,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(2050, 1, 1),
            providedProductIds);
    }

    private Entitlement mockEntitlement(Consumer consumer, String productId,
        Date start, Date end, String ... providedProductIds) {

        Set<ProvidedProduct> provided = new HashSet<ProvidedProduct>();
        for (String pid : providedProductIds) {
            provided.add(new ProvidedProduct(pid, pid));
        }
        Pool p = new Pool(owner, productId, productId, provided,
            new Long(1000), start, end, "1000", "1000", "1000");
        Entitlement e = new Entitlement(p, consumer, 1);
        return e;
    }

    private Entitlement mockBaseStackedEntitlement(Consumer consumer, String stackId,
        String productId, String ... providedProductIds) {

        Entitlement e = mockEntitlement(consumer, productId, providedProductIds);

        Random gen = new Random();
        int id = gen.nextInt(Integer.MAX_VALUE);
        e.setId(String.valueOf(id));

        Pool p = e.getPool();

        // Setup the attributes for stacking:
        p.addProductAttribute(new ProductPoolAttribute("stacking_id", stackId, productId));

        return e;
    }

    private Entitlement mockStackedEntitlement(Consumer consumer, String stackId,
        String productId, String ... providedProductIds) {
        Entitlement ent = this.mockBaseStackedEntitlement(consumer, stackId, productId,
            providedProductIds);
        ent.getPool().setProductAttribute("sockets", "2", productId);
        return ent;
    }

    private Entitlement mockInstanceEntitlement(Consumer consumer, String stackId,
        String instanceMultiplier, String productId, String ... providedProductIds) {
        Entitlement ent = this.mockBaseStackedEntitlement(consumer, stackId, productId,
            providedProductIds);
        ent.getPool().setProductAttribute("sockets", "2", productId);
        ent.getPool().setProductAttribute("instance_multiplier", instanceMultiplier,
            productId);
        return ent;
    }

    private Entitlement mockHostRestrictedEntitlement(Consumer consumer, String stackId,
        String productId, String ... providedProductIds) {
        Entitlement ent = this.mockBaseStackedEntitlement(consumer, stackId, productId,
            providedProductIds);
        ent.getPool().setProductAttribute("sockets", "2", productId);
        ent.getPool().setAttribute("requires_host", "SOMEUUID");
        return ent;
    }

    private Consumer mockConsumerWithTwoProductsAndNoEntitlements() {
        return mockConsumer(PRODUCT_1, PRODUCT_2);
    }

    private Consumer mockFullyEntitledConsumer() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1, PRODUCT_2));
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
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(1, status.getNonCompliantProducts().size());
        assertTrue(status.getNonCompliantProducts().contains(PRODUCT_2));

        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));

        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }

    @Test
    public void fullyEntitled() {
        Consumer c = mockFullyEntitledConsumer();

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());

        // Our one entitlement should cover both of these:
        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }

    @Test
    public void testArchitectureMismatch() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("arch",
            "PPC64", "Awesome Product"));
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_2));
        ents.get(1).getPool().addProductAttribute(new ProductPoolAttribute("arch",
            "x86_64", "Awesome Product"));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        // Our one entitlement should not cover both of these:
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
        assertEquals("partial", status.getStatus());
    }

    @Test
    public void testMultiArchitectureMatch() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("arch",
            "PPC64,x86_64", "Awesome Product"));
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_2));
        ents.get(1).getPool().addProductAttribute(new ProductPoolAttribute("arch",
            "x86_64", "Awesome Product"));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        // Our one entitlement should not cover both of these:
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertEquals("valid", status.getStatus());
    }

    /*
     * Test an installed product which has a normal non-stacked entitlement, but to a
     * product which does not cover sufficient sockets for the system.
     */
    @Test
    public void regularEntButLackingSocketCoverage() {
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "4", PRODUCT_1));
        ents.get(0).setQuantity(1000); // quantity makes no difference outside stacking
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
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
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // One entitlement that only provides four sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "4", PRODUCT_1));

        // One entitlement that provides ten sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(1).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "10", PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
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
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // One entitlement that only provides four sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "4", PRODUCT_1));

        // One entitlement that provides 6 sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(1).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "6", PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
    }

    /*
     * Test an installed product which is fully covered by a normal entitlement, but also
     * by a partial stack. In this case it should show up as a compliant product,
     * a partial stack, but not a partially compliant product.
     */
    @Test
    public void regularEntPlusPartialStack() {
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getPartialStacks().keySet().contains(STACK_ID_1));
    }

    @Test
    public void regularEntPlusArchMismatchStack() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new
            ProductPoolAttribute("arch", "x86_64", "Awesome Product"));
        ents.get(1).getPool().addProductAttribute(new
            ProductPoolAttribute("arch", "PPC64", "Awesome Product"));
        ents.get(2).getPool().addProductAttribute(new
            ProductPoolAttribute("arch", "x86_64", "Awesome Product"));
        ents.get(3).getPool().addProductAttribute(new
            ProductPoolAttribute("arch", "PPC64", "Awesome Product"));
        ents.get(4).getPool().addProductAttribute(new
            ProductPoolAttribute("arch", "PPC64", "Awesome Product"));
        mockEntCurator(c, ents);

        ComplianceStatus status =
            compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertEquals("partial", status.getStatus());
    }

    public void testComplianceDoesNotEnforceSocketsWhenAttributeNotSet() {
        // Consumer with 8 sockets:
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));

        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
    }

    @Test
    public void testComplianceCountsZeroPoolSocketsAsNotSet() {
        // Consumer with 8 sockets:
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "0", PRODUCT_1));

        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
    }

    @Test
    public void testComplianceCountsUndefinedPoolSocketsAsInfinite() {
        // Consumer with 8 sockets:
        Consumer c = mockConsumer(PRODUCT_1);
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // One entitlement that only provides four sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));

        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(0, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
    }

    // Test a fully stacked scenario:
    @Test
    public void compliantStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2).size());
    }

    @Test
    public void testArchMismatchStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.get(0).getPool().addProductAttribute(
            new ProductPoolAttribute("arch", "x86_64", "Awesome Product"));
        ents.get(1).getPool().addProductAttribute(
            new ProductPoolAttribute("arch", "PPC64", "Awesome Product"));
        ents.get(2).getPool().addProductAttribute(
            new ProductPoolAttribute("arch", "x86_64", "Awesome Product"));
        ents.get(3).getPool().addProductAttribute(
            new ProductPoolAttribute("arch", "x86_64", "Awesome Product"));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertEquals(4, status.getPartiallyCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getPartiallyCompliantProducts().get(PRODUCT_2).size());
        assertEquals(1, status.getPartialStacks().size());
        assertTrue(status.getPartialStacks().keySet().contains(STACK_ID_1));
        assertEquals("partial", status.getStatus());
    }

    // Test a partially stacked scenario:
    @Test
    public void partiallyCompliantStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // Three entitlements, 2 sockets each, is not enough for our 8 sockets:
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());

        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(3, status.getPartiallyCompliantProducts().get(PRODUCT_1).size());
        assertEquals(3, status.getPartiallyCompliantProducts().get(PRODUCT_2).size());
    }

    // Test having more stacked entitlements than we need:
    @Test
    public void overCompliantStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(8, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(8, status.getCompliantProducts().get(PRODUCT_2).size());
    }

    @Test
    public void notInstalledButPartiallyStacked() {
        // TODO: spoke to PM, this is considered invalid even though the product is
        // not technically installed. (yet)
        Consumer c = mockConsumer(); // nothing installed
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
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
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_3));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());

        assertEquals(3, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(2, status.getPartiallyCompliantProducts().get(PRODUCT_1).size());
        assertEquals(1, status.getPartiallyCompliantProducts().get(PRODUCT_2).size());
        assertEquals(1, status.getPartiallyCompliantProducts().get(PRODUCT_3).size());

        assertEquals(1, status.getPartialStacks().size());
    }

    @Test
    public void fullStackProvidingDiffProducts() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2, PRODUCT_3);
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_3));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_2));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(3, status.getCompliantProducts().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertEquals(2, status.getCompliantProducts().get(PRODUCT_1).size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));
        assertEquals(3, status.getCompliantProducts().get(PRODUCT_2).size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(1, status.getCompliantProducts().get(PRODUCT_3).size());

        assertEquals(0, status.getPartialStacks().size());
    }

    @Test
    public void partialStackAndFullStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // Partial stack:
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        // Full stack providing only product 1:
        ents.add(mockStackedEntitlement(c, STACK_ID_2, "Awesome Product 2",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_2, "Awesome Product 2",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_2, "Awesome Product 2",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_2, "Awesome Product 2",
            PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));

        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2));

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
        Consumer consumer = mockConsumer("Only One Installed Prod");
        Date expectedOnDate = TestUtil.createDate(9999, 4, 12);
        ComplianceStatus status = compliance.getStatus(consumer, expectedOnDate);
        assertEquals(expectedOnDate, status.getCompliantUntil());
    }

    @Test
    public void compliantUntilDateIsDateOfFirstEntitlementToExpireCausingNonCompliant() {
        Consumer consumer = mockConsumer(PRODUCT_1, PRODUCT_2);

        Date start = TestUtil.createDate(2005, 6, 12);

        Entitlement ent1 = mockEntitlement(consumer, "Provides Product 1 For Short Period",
            start, TestUtil.createDate(2005, 6, 22), PRODUCT_1);

        Entitlement ent2 = mockEntitlement(consumer, "Provides Product 1 past Ent3",
            TestUtil.createDate(2005, 6, 20), TestUtil.createDate(2005, 7, 28), PRODUCT_1);

        Entitlement ent3 = mockEntitlement(consumer, "Provides Product 2 Past Ent1",
            start, TestUtil.createDate(2005, 7, 18), PRODUCT_2);

        mockEntCurator(consumer, Arrays.asList(ent1, ent2, ent3));

        // Set up entitlements at specific dates.
//        Date statusDate = TestUtil.createDate(2005, 6, 14);
//        when(entCurator.listByConsumerAndDate(eq(consumer),
//            eq(statusDate))).thenReturn(Arrays.asList(ent1, ent3));
//
//        when(entCurator.listByConsumerAndDate(eq(consumer),
//            eq(addSecond(ent1.getEndDate())))).thenReturn(Arrays.asList(ent2, ent3));
//
//        when(entCurator.listByConsumerAndDate(eq(consumer),
//            eq(addSecond(ent2.getEndDate())))).thenReturn(
//                Arrays.asList(new Entitlement[0]));
//
//        Date expectedDate = addSecond(ent3.getEndDate());
//        when(entCurator.listByConsumerAndDate(eq(consumer),
//            eq(expectedDate))).thenReturn(Arrays.asList(ent2));

        Date statusDate = TestUtil.createDate(2005, 6, 14);
        Date expectedDate = addSecond(ent3.getEndDate());
        ComplianceStatus status = compliance.getStatus(consumer, statusDate);
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
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // Partial stack: covers only 4 sockets... consumer has 8.
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        mockEntCurator(c, ents);

        Date expectedOnDate = TestUtil.createDate(2011, 8, 30);
        ComplianceStatus status = compliance.getStatus(c, expectedOnDate);
        assertEquals(1, status.getPartialStacks().size());
        assertEquals(expectedOnDate, status.getCompliantUntil());
    }

    // NOTE: This scenario should NEVER happen since listByConsumerAndDate should
    //       never return dates before the specified date. This test exists to
    //       test the guard clauses in the ComplianceRulesHelper in case it ever happened.
    @Test
    public void expiredEntitlementIsIgnoredWhenCalculatingCompliantUntilDate() {
        Consumer consumer = mockConsumer(PRODUCT_1);

        Date start = TestUtil.createDate(2005, 6, 12);

        Entitlement expired = mockEntitlement(consumer, "Provides Product 1 past Ent3",
            TestUtil.createDate(2005, 5, 20), TestUtil.createDate(2005, 6, 2), PRODUCT_1);

        Entitlement ent = mockEntitlement(consumer, "Provides Product 1 For Short Period",
            start, TestUtil.createDate(2005, 6, 22), PRODUCT_1);

        // Set up entitlements at specific dates.
        mockEntCurator(consumer, Arrays.asList(expired, ent));

        Date expectedDate = addSecond(ent.getEndDate());
        ComplianceStatus status = compliance.getStatus(consumer, start);
        assertEquals(expectedDate, status.getCompliantUntil());
    }

    @Test
    public void statusGreenWhenConsumerHasNoInstalledProducts() {
        Consumer c = mockConsumer();
        List<Entitlement> ents = new LinkedList<Entitlement>();
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
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
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
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void statusRedWhenNonCompliantProductAndPartialProduct() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void statusRedOneWhenNonCompliantProductAndCompliantAndPartial() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2, PRODUCT_3);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        ents.add(mockEntitlement(c, "Another Product", PRODUCT_3));
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

        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        assertTrue(compliance.isStackCompliant(c, STACK_ID_1, ents));
    }

    @Test
    public void stackIsNotCompliant() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.cpu_socket(s)", "4");

        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        ents.add(mockEntitlement(c, "Another Product", PRODUCT_3));
        assertFalse(compliance.isStackCompliant(c, STACK_ID_1, ents));
    }

    @Test
    public void entIsCompliantIfSocketsNotSetOnEntPool() {
        Consumer c = mockConsumer(PRODUCT_1);
        c.setFact("cpu.cpu_socket(s)", "2");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        assertTrue(compliance.isEntitlementCompliant(c, ent, new Date()));
    }

    @Test
    public void entIsCompliantWhenSocketsAreCovered() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_1 });
        c.setFact("cpu.cpu_socket(s)", "4");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setProductAttribute("sockets", "4", PRODUCT_1);
        assertTrue(compliance.isEntitlementCompliant(c, ent, new Date()));
    }

    @Test
    public void entIsNotCompliantWhenSocketsAreNotCovered() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_1 });
        c.setFact("cpu.cpu_socket(s)", "8");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setProductAttribute("sockets", "4", PRODUCT_1);
        assertFalse(compliance.isEntitlementCompliant(c, ent, new Date()));
    }

    @Test
    public void entPartialStackNoProductsInstalled() {
        Consumer c = mockConsumer();
        c.setFact("uname.machine", "x86_64");
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(
            new ProductPoolAttribute("arch", "x86_64", "Awesome Product"));
        ents.get(1).getPool().addProductAttribute(
            new ProductPoolAttribute("arch", "PPC64", "Awesome Product"));
        ents.get(2).getPool().addProductAttribute(
            new ProductPoolAttribute("arch", "x86_64", "Awesome Product"));
        ents.get(3).getPool().addProductAttribute(
            new ProductPoolAttribute("arch", "PPC64", "Awesome Product"));
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
        Consumer c = mockConsumer(new String[]{ PRODUCT_1 });
        c.setFact("cpu.core(s)_per_socket", "4");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setProductAttribute("cores", "32", PRODUCT_1);
        mockEntCurator(c, Arrays.asList(ent));
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productPartiallyCoveredWhenSingleEntitlementDoesNotCoverAllCores() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_1 });
        c.setFact("cpu.core(s)_per_socket", "8");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setProductAttribute("cores", "4", PRODUCT_1);
        mockEntCurator(c, Arrays.asList(ent));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    // Cores stacking tests
    @Test
    public void productCoveredWhenStackedEntsCoverCores() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("cpu.core(s)_per_socket", "8");

        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().setProductAttribute("cores", "32", PRODUCT_1);

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().setProductAttribute("cores", "32", PRODUCT_2);

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productPartiallyCoveredWhenStackingEntsDoesNotCoverCores() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("cpu.core(s)_per_socket", "8");

        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        // Cores are not covered.
        ent1.getPool().setProductAttribute("cores", "4", PRODUCT_1);

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        // Mock consumer has 8 sockets by default.
        ent2.getPool().setProductAttribute("cores", "1", PRODUCT_1);

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    // Multi-attribute stacking tests.
    @Test
    public void productCoveredWhenStackedEntitlementCoversBothSocketsAndCores() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().setProductAttribute("cores", "32", PRODUCT_1);
        ent1.getPool().setProductAttribute("sockets", "4", PRODUCT_1);

        mockEntCurator(c, Arrays.asList(ent1));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productCoveredWhenTwoStackedEntsCoversBothSocketsAndCores() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().setProductAttribute("cores", "24", PRODUCT_1);
        ent1.getPool().setProductAttribute("sockets", "2", PRODUCT_1);

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().setProductAttribute("cores", "8", PRODUCT_2);
        ent2.getPool().setProductAttribute("sockets", "2", PRODUCT_2);

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productCoveredWhenTwoStackedEntsCoverBothSocketsAndCoresSeperately() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().setProductAttribute("sockets", "4", PRODUCT_1);

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().setProductAttribute("cores", "32", PRODUCT_2);

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void productPartiallyCoveredWhenTwoStackedEntsCoverOnlyCores() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().setProductAttribute("sockets", "2", PRODUCT_1);

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().setProductAttribute("cores", "8", PRODUCT_2);

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    @Test
    public void productPartiallyCoveredWhenStackedEntsCoverSocketsOnly() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("cpu.cpu_socket(s)", "4");
        c.setFact("cpu.core(s)_per_socket", "8");


        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().setProductAttribute("sockets", "4", PRODUCT_1);

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().setProductAttribute("cores", "4", PRODUCT_2);

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    // RAM stacking tests
    @Test
    public void productCoveredWhenStackedEntsCoverRam() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("memory.memtotal", "8000000"); // 8GB RAM

        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        ent1.getPool().setProductAttribute("ram", "4", PRODUCT_1);

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        ent2.getPool().setProductAttribute("ram", "4", PRODUCT_2);

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_3));
    }

    @Test
    public void productPartiallyCoveredWhenStackingEntsDoesNotCoverRam() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_3 });
        c.setFact("memory.memtotal", "8000000"); // 8GB

        Entitlement ent1 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_1, PRODUCT_3);
        // Cores are not covered.
        ent1.getPool().setProductAttribute("ram", "4", PRODUCT_1);

        Entitlement ent2 = mockBaseStackedEntitlement(c, STACK_ID_1, PRODUCT_2, PRODUCT_3);
        // Mock consumer has 8 sockets by default.
        ent2.getPool().setProductAttribute("ram", "1", PRODUCT_1);

        mockEntCurator(c, Arrays.asList(ent1, ent2));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_3));
        assertEquals(0, status.getCompliantProducts().size());
    }

    @Test
    public void instanceBasedPhysicalGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(8);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));
    }

    @Test
    public void instanceBasedPhysicalStackedGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(6);
        ents.get(1).setQuantity(2);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));
    }

    @Test
    public void instanceBasedPhysicalYellow() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(7);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2));
    }

    @Test
    public void instanceBasedVirtualGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("virt.is_guest", "true");
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockInstanceEntitlement(c, STACK_ID_1, "2", "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(1);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));
    }

    @Test
    public void hostRestrictedVirtualGreen() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        c.setFact("virt.is_guest", "true");
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockHostRestrictedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.get(0).setQuantity(1);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(ComplianceStatus.GREEN, status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));
    }

    /*
     * Testing behaviour from a (possibly) temporary hack where we skip compliance
     * calculation for distributor consumers, as these should never have installed products
     * and status in general is not really applicable to them.
     *
     * This test may need to be removed if we restore calculation for distributors at some
     * point.
     */
    @Test
    public void distributorStatusAlwaysGreen() {
        Consumer c = mockConsumerWithTwoProductsAndNoEntitlements();
        c.setType(new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN));

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(ComplianceStatus.GREEN, status.getStatus());
    }

    @Test
    public void virtLimitSingleEntitlementCovered() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_1 });
        c.setFact("cpu.core(s)_per_socket", "4");
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setProductAttribute("cores", "32", PRODUCT_1);
        ent.getPool().setProductAttribute("guest_limit", "8", PRODUCT_1);

        mockEntCurator(c, Arrays.asList(ent));
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
    }

    @Test
    public void virtLimitSingleEntitlementPartial() {
        Consumer c = mockConsumer(new String[]{ PRODUCT_1 });
        c.setFact("cpu.core(s)_per_socket", "4");
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setProductAttribute("cores", "32", PRODUCT_1);
        ent.getPool().setProductAttribute("guest_limit", "4", PRODUCT_1);

        mockEntCurator(c, Arrays.asList(ent));
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
        assertEquals(ComplianceStatus.YELLOW, status.getStatus());
    }

    @Test
    public void partiallyCompliantVirtLimitStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().setProductAttribute("guest_limit", "4", PRODUCT_1);
        }
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());

        assertEquals(2, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getPartiallyCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(4, status.getPartiallyCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getPartiallyCompliantProducts().get(PRODUCT_2).size());
    }

    @Test
    public void fullyCompliantVirtLimitStackWithInactiveGuests() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c));
        }
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().setProductAttribute("guest_limit", "4", PRODUCT_1);
        }
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2).size());
    }

    @Test
    public void fullyCompliantVirtLimitStack() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().setProductAttribute("guest_limit", "8", PRODUCT_1);
        }
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2).size());
    }

    @Test
    public void fullyCompliantVirtLimitStackVaryingLimits() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().setProductAttribute("guest_limit", "1", PRODUCT_1);
        }
        ents.get(0).getPool().setProductAttribute("guest_limit", "5", PRODUCT_1);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2).size());
    }

    @Test
    public void fullyCompliantVirtLimitStackWithUnlimited() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().setProductAttribute("guest_limit", "1", PRODUCT_1);
        }
        ents.get(0).getPool().setProductAttribute("guest_limit", "-1", PRODUCT_1);
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2).size());
    }

    @Test
    public void fullyCompliantVirtLimitStackAllUnlimited() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        for (Entitlement ent : ents) {
            ent.getPool().setProductAttribute("guest_limit", "-1", PRODUCT_1);
        }
        mockEntCurator(c, ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2).size());
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
        List<Entitlement> ents = new LinkedList<Entitlement>();

        Entitlement mockServerEntitlement = mockEntitlement(c, "Awesome OS server",
            PRODUCT_1, PRODUCT_2);
        mockServerEntitlement.getPool().setProductAttribute("guest_limit", "4", PRODUCT_1);
        ents.add(mockServerEntitlement);
        mockEntCurator(c, ents);

        // Before we add the hypervisor, this product shouldn't be compliant
        ComplianceStatus status =
            compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        // Should be partial
        assertEquals("partial", status.getStatus());
        assertEquals(2, status.getPartiallyCompliantProducts().size());

        Entitlement mockHypervisorEntitlement = mockEntitlement(c,
            "Awesome Enterprise Hypervisor", PRODUCT_1, PRODUCT_2);
        mockHypervisorEntitlement.getPool()
            .setProductAttribute("guest_limit", "-1", PRODUCT_1);
        ents.add(mockHypervisorEntitlement);

        // Now that we've added the hypervisor,
        // the base guest_limit of 4 should be overridden
        status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(2, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(1, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(1, status.getCompliantProducts().get(PRODUCT_2).size());
    }

    @Test
    public void isEntFullyCompliantOverriddenVirtLimit() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<Entitlement>();

        Entitlement mockServerEntitlement = mockEntitlement(c, "Awesome OS server",
            PRODUCT_1, PRODUCT_2);
        mockServerEntitlement.getPool().setProductAttribute("guest_limit", "4", PRODUCT_1);
        ents.add(mockServerEntitlement);

        Entitlement mockHypervisorEntitlement = mockEntitlement(c,
            "Awesome Enterprise Hypervisor", PRODUCT_1, PRODUCT_2);
        mockHypervisorEntitlement.getPool()
            .setProductAttribute("guest_limit", "-1", PRODUCT_1);
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
        List<Entitlement> ents = new LinkedList<Entitlement>();

        Entitlement mockServerEntitlement = mockEntitlement(c, "Awesome OS server",
            PRODUCT_1, PRODUCT_2);
        mockServerEntitlement.getPool().setProductAttribute("guest_limit", "4", PRODUCT_1);
        ents.add(mockServerEntitlement);

        mockEntCurator(c, ents);

        // The guest limit has not been modified, should not be compliant.
        assertFalse(compliance.isEntitlementCompliant(c,
            mockServerEntitlement, new Date()));
    }

    @Test
    public void isStackFullyCompliantOverriddenVirtLimit() {
        Consumer c = mockConsumer(PRODUCT_1, PRODUCT_2);
        for (int i = 0; i < 5; i++) {
            c.addGuestId(new GuestId("" + i, c, activeGuestAttrs));
        }
        List<Entitlement> ents = new LinkedList<Entitlement>();

        Entitlement mockServerEntitlement = mockStackedEntitlement(c, "mockServerStack",
            PRODUCT_1, PRODUCT_2);
        mockServerEntitlement.getPool().setProductAttribute("guest_limit", "4", PRODUCT_1);
        mockServerEntitlement.getPool().setProductAttribute("sockets", "2", PRODUCT_1);
        mockServerEntitlement.setQuantity(4);
        ents.add(mockServerEntitlement);

        Entitlement mockHypervisorEntitlement = mockEntitlement(c,
            "Awesome Enterprise Hypervisor", PRODUCT_1, PRODUCT_2);
        mockHypervisorEntitlement.getPool()
            .setProductAttribute("guest_limit", "-1", PRODUCT_1);
        ents.add(mockHypervisorEntitlement);

        // Now that we've added the hypervisor,
        // the base guest_limit of 4 should be overridden
        assertTrue(compliance.isStackCompliant(c, "mockServerStack", ents));
    }

    private void mockEntCurator(Consumer c, List<Entitlement> ents) {
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);
    }
}
