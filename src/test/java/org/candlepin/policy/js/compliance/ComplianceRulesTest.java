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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.candlepin.policy.js.JsRulesProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;



/**
 * ComplianceTest
 */
public class ComplianceRulesTest {
    private Owner owner;
    private ComplianceRules compliance;

    private static final String RULES_FILE = "/rules/default-rules.js";

    private static final String PRODUCT_1 = "product1";
    private static final String PRODUCT_2 = "product2";
    private static final String PRODUCT_3 = "product3";
    private static final String STACK_ID_1 = "my-stack-1";
    private static final String STACK_ID_2 = "my-stack-2";

    @Mock private EntitlementCurator entCurator;
    @Mock private RulesCurator rulesCuratorMock;
    private JsRulesProvider provider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        provider = new JsRulesProvider(rulesCuratorMock);
        compliance = new ComplianceRules(provider.get(), entCurator);
        owner = new Owner("test");
    }

    private Consumer mockConsumer(String [] installedProducts) {
        Consumer c = new Consumer();
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
            new Long(1000), start, end, "1000", "1000");
        Entitlement e = new Entitlement(p, consumer, p.getStartDate(), p.getEndDate(), 1);
        return e;
    }

    private Entitlement mockStackedEntitlement(Consumer consumer, String stackId,
        String productId, String ... providedProductIds) {

        Entitlement e = mockEntitlement(consumer, productId, providedProductIds);

        Random gen = new Random();
        int id = gen.nextInt(Integer.MAX_VALUE);
        e.setId(String.valueOf(id));

        Pool p = e.getPool();

        // Setup the attributes for stacking:
        p.addProductAttribute(new ProductPoolAttribute("stacking_id", stackId, productId));
        p.addProductAttribute(new ProductPoolAttribute("sockets", "2", productId));

        return e;
    }

    private Consumer mockConsumerWithTwoProductsAndNoEntitlements() {
        return mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
    }

    private Consumer mockFullyEntitledConsumer() {
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1, PRODUCT_2));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);
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
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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

    /*
     * Test an installed product which has a normal non-stacked entitlement, but to a
     * product which does not cover sufficient sockets for the system.
     */
    @Test
    public void regularEntButLackingSocketCoverage() {
        Consumer c = mockConsumer(new String [] {PRODUCT_1});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "4", PRODUCT_1));
        ents.get(0).setQuantity(1000); // quantity makes no difference outside stacking
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer c = mockConsumer(new String [] {PRODUCT_1});
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // One entitlement that only provides four sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "4", PRODUCT_1));

        // One entitlement that provides ten sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(1).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "10", PRODUCT_1));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer c = mockConsumer(new String [] {PRODUCT_1});
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // One entitlement that only provides four sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(0).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "4", PRODUCT_1));

        // One entitlement that provides 6 sockets:
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.get(1).getPool().addProductAttribute(new ProductPoolAttribute("sockets",
            "6", PRODUCT_1));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer c = mockConsumer(new String [] {PRODUCT_1});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getPartialStacks().size());

        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getPartialStacks().keySet().contains(STACK_ID_1));
    }

    // Test a fully stacked scenario:
    @Test
    public void compliantStack() {
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());

        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_1));
        assertTrue(status.getCompliantProducts().keySet().contains(PRODUCT_2));

        assertEquals(4, status.getCompliantProducts().get(PRODUCT_1).size());
        assertEquals(4, status.getCompliantProducts().get(PRODUCT_2).size());
    }

    // Test a partially stacked scenario:
    @Test
    public void partiallyCompliantStack() {
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // Three entitlements, 2 sockets each, is not enough for our 8 sockets:
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
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
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer c = mockConsumer(new String [] {}); // nothing installed
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));

        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getPartialStacks().size());

        assertTrue(status.getPartialStacks().keySet().contains(STACK_ID_1));
    }

    @Test
    public void partialStackProvidingDiffProducts() {
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2, PRODUCT_3});
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_3));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2, PRODUCT_3});
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_3));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_2));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
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
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer consumer = mockConsumer(new String[0]);
        ComplianceStatus status = compliance.getStatus(consumer, new Date());
        assertNull(status.getCompliantUntil());
    }

    @Test
    public void compliantUntilDateIsOnDateWhenInstalledProductsButNoEntitlements() {
        Consumer consumer = mockConsumer(new String[]{ "Only One Installed Prod"});
        Date expectedOnDate = TestUtil.createDate(20011, 4, 12);
        ComplianceStatus status = compliance.getStatus(consumer, expectedOnDate);
        assertEquals(expectedOnDate, status.getCompliantUntil());
    }

    @Test
    public void compliantUntilDateIsDateOfFirstEntitlementToExpireCausingNonCompliant() {
        Consumer consumer = mockConsumer(new String[]{ PRODUCT_1, PRODUCT_2 });

        Date start = TestUtil.createDate(2005, 6, 12);

        Entitlement ent1 = mockEntitlement(consumer, "Provides Product 1 For Short Period",
            start, TestUtil.createDate(2005, 6, 22), PRODUCT_1);

        Entitlement ent2 = mockEntitlement(consumer, "Provides Product 1 past Ent3",
            TestUtil.createDate(2005, 6, 20), TestUtil.createDate(2005, 7, 28), PRODUCT_1);

        Entitlement ent3 = mockEntitlement(consumer, "Provides Product 2 Past Ent1",
            start, TestUtil.createDate(2005, 7, 18), PRODUCT_2);

        // Set up entitlements at specific dates.
        Date statusDate = TestUtil.createDate(2005, 6, 14);
        when(entCurator.listByConsumerAndDate(eq(consumer),
            eq(statusDate))).thenReturn(Arrays.asList(ent1, ent3));

        when(entCurator.listByConsumerAndDate(eq(consumer),
            eq(addSecond(ent1.getEndDate())))).thenReturn(Arrays.asList(ent2, ent3));

        when(entCurator.listByConsumerAndDate(eq(consumer),
            eq(addSecond(ent2.getEndDate())))).thenReturn(
                Arrays.asList(new Entitlement[0]));

        Date expectedDate = addSecond(ent3.getEndDate());
        when(entCurator.listByConsumerAndDate(eq(consumer),
            eq(expectedDate))).thenReturn(Arrays.asList(ent2));

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
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
        List<Entitlement> ents = new LinkedList<Entitlement>();

        // Partial stack: covers only 4 sockets... consumer has 8.
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product",
            PRODUCT_1, PRODUCT_2));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer consumer = mockConsumer(new String[]{ PRODUCT_1 });

        Date start = TestUtil.createDate(2005, 6, 12);

        Entitlement expired = mockEntitlement(consumer, "Provides Product 1 past Ent3",
            TestUtil.createDate(2005, 5, 20), TestUtil.createDate(2005, 6, 2), PRODUCT_1);

        Entitlement ent = mockEntitlement(consumer, "Provides Product 1 For Short Period",
            start, TestUtil.createDate(2005, 6, 22), PRODUCT_1);

        // Set up entitlements at specific dates.
        when(entCurator.listByConsumerAndDate(eq(consumer),
            eq(start))).thenReturn(Arrays.asList(expired, ent));

        when(entCurator.listByConsumerAndDate(eq(consumer),
            eq(addSecond(ent.getEndDate())))).thenReturn(Arrays.asList(new Entitlement[0]));

        Date expectedDate = addSecond(ent.getEndDate());
        ComplianceStatus status = compliance.getStatus(consumer, start);
        assertEquals(expectedDate, status.getCompliantUntil());
    }

    @Test
    public void statusGreenWhenConsumerHasNoInstalledProducts() {
        Consumer c = mockConsumer(new String [] {});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);
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
        Consumer c = mockConsumer(new String [] {PRODUCT_1});
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

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
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", PRODUCT_1));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void statusRedWhenNonCompliantProductAndPartialProduct() {
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2});
        List<Entitlement> ents = new LinkedList<Entitlement>();

        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void statusRedOneWhenNonCompliantProductAndCompliantAndPartial() {
        Consumer c = mockConsumer(new String [] {PRODUCT_1, PRODUCT_2, PRODUCT_3});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        ents.add(mockEntitlement(c, "Another Product", PRODUCT_3));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        assertEquals(1, status.getPartiallyCompliantProducts().size());
        assertEquals(1, status.getCompliantProducts().size());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals(ComplianceStatus.RED, status.getStatus());
    }

    @Test
    public void stackIsCompliant() {
        Consumer c = mock(Consumer.class);
        when(c.hasFact("cpu.cpu_socket(s)")).thenReturn(true);
        when(c.getFact("cpu.cpu_socket(s)")).thenReturn("2");

        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        assertTrue(compliance.isStackCompliant(c, STACK_ID_1, ents));
    }

    @Test
    public void stackIsNotCompliant() {
        Consumer c = mock(Consumer.class);
        when(c.hasFact("cpu.cpu_socket(s)")).thenReturn(true);
        when(c.getFact("cpu.cpu_socket(s)")).thenReturn("4");

        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockStackedEntitlement(c, STACK_ID_1, "Awesome Product", PRODUCT_1));
        ents.add(mockEntitlement(c, "Another Product", PRODUCT_3));
        assertFalse(compliance.isStackCompliant(c, STACK_ID_1, ents));
    }

    @Test
    public void entIsCompliantIfSocketsNotSetOnEntPool() {
        Consumer c = mock(Consumer.class);
        when(c.hasFact("cpu.cpu_socket(s)")).thenReturn(true);
        when(c.getFact("cpu.cpu_socket(s)")).thenReturn("2");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        assertTrue(compliance.isEntitlementCompliant(c, ent));
    }

    @Test
    public void entIsCompliantWhenSocketsAreCovered() {
        Consumer c = mock(Consumer.class);
        when(c.hasFact("cpu.cpu_socket(s)")).thenReturn(true);
        when(c.getFact("cpu.cpu_socket(s)")).thenReturn("4");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setProductAttribute("sockets", "4", PRODUCT_1);
        assertTrue(compliance.isEntitlementCompliant(c, ent));
    }

    @Test
    public void entIsNotCompliantWhenSocketsAreNotCovered() {
        Consumer c = mock(Consumer.class);
        when(c.hasFact("cpu.cpu_socket(s)")).thenReturn(true);
        when(c.getFact("cpu.cpu_socket(s)")).thenReturn("8");

        Entitlement ent = mockEntitlement(c, PRODUCT_1);
        ent.getPool().setProductAttribute("sockets", "4", PRODUCT_1);
        assertFalse(compliance.isEntitlementCompliant(c, ent));
    }
}
