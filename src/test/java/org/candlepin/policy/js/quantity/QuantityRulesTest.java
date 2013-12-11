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
package org.candlepin.policy.js.quantity;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * QuantityRulesTest
 */
public class QuantityRulesTest {

    private static final String SOCKET_ATTRIBUTE = "sockets";
    private static final String INSTANCE_ATTRIBUTE = "instance_multiplier";
    private static final String SOCKET_FACT = "cpu.cpu_socket(s)";
    private static final String CORES_ATTRIBUTE = "cores";
    private static final String CORES_FACT = "cpu.core(s)_per_socket";
    private static final String IS_VIRT = "virt.is_guest";
    private static final String GUEST_LIMIT_ATTRIBUTE = "guest_limit";

    private Consumer consumer;
    private Pool pool;
    private Product product;
    private Owner owner;
    private QuantityRules quantityRules;
    private JsRunnerProvider provider;

    @Mock private RulesCurator rulesCuratorMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        provider = new JsRunnerProvider(rulesCuratorMock);
        quantityRules = new QuantityRules(provider.get());

        owner = new Owner("Test Owner " + TestUtil.randomInt());
        product = TestUtil.createProduct();
        pool = TestUtil.createPool(owner, product);

        consumer = TestUtil.createConsumer(owner);
        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool,
            new EntitlementCertificate());

        Set<Entitlement> entSet = new HashSet<Entitlement>();
        entSet.add(e);

        pool.setEntitlements(entSet);
        pool.setProductAttribute("multi-entitlement", "yes", product.getId());
        pool.setProductAttribute("stacking_id", "1", product.getId());
    }

    private Entitlement createValidEntitlement(Pool p) {
        Entitlement e = TestUtil.createEntitlement(owner, consumer, p, null);

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, 1);

        Date dayFromNow = cal.getTime();

        cal.add(Calendar.DATE, -2);
        Date dayAgo = cal.getTime();

        e.setCreated(dayAgo);
        p.setEndDate(dayFromNow);
        return e;
    }

    @Test
    public void testNonMultiEntitlementPool() {
        pool.setProductAttribute("multi-entitlement", "no", product.getId());
        SuggestedQuantity suggested = quantityRules.getSuggestedQuantity(pool,
            new Consumer(), new Date());
        assertEquals(new Long(1), suggested.getSuggested());
    }

    @Test
    public void testPhysicalDefaultToNumSocketsBySocketCount() {
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testPhysicalRoundsUp() {
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "3", product.getId());
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testPhysicalAccountsForCurrentlyConsumed() {
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "1", product.getId());

        Entitlement e = createValidEntitlement(pool);
        e.setQuantity(2);

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testVirtDefaultToNumCpusByVcpuCount() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(CORES_FACT, "8");
        pool.setProductAttribute(CORES_ATTRIBUTE, "4", product.getId());
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testVirtUsesSocketsIfVcpuDoesNotExist() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testVirtUses1IfNoSocketsAndNoVcpu() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(SOCKET_FACT, "4");
        consumer.setFact(CORES_FACT, "8");
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(1), suggested.getSuggested());
    }

    @Test
    public void testVirtRoundsUp() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(CORES_FACT, "8");
        pool.setProductAttribute(CORES_ATTRIBUTE, "6", product.getId());
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testVirtAccountsForCurrentlyConsumed() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(CORES_FACT, "4");
        pool.setProductAttribute(CORES_ATTRIBUTE, "1", product.getId());

        Entitlement e = createValidEntitlement(pool);
        e.setQuantity(2);

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testUnlimitedQuantity() {
        consumer.setFact(SOCKET_FACT, "8");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        pool.setQuantity(new Long(-1));
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
    }

    @Test
    public void testIsNotVirtWhenFactIsFalse() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
        assertEquals(new Long(1), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnPhysical() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        pool.setProductAttribute(INSTANCE_ATTRIBUTE, "2", product.getId());

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
        assertEquals(new Long(2), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnPhysicalNotEnoughAvailable() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "40"); // lots of ents required
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        pool.setProductAttribute(INSTANCE_ATTRIBUTE, "2", product.getId());

        pool.setQuantity(4L);
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
        assertEquals(new Long(2), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnPhysicalNotEnoughAvailableUneven() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "40"); // lots of ents required
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        pool.setProductAttribute(INSTANCE_ATTRIBUTE, "2", product.getId());

        pool.setQuantity(3L);
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
        assertEquals(new Long(2), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnGuest() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        pool.setProductAttribute(INSTANCE_ATTRIBUTE, "2", product.getId());

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(1), suggested.getSuggested());
        assertEquals(new Long(1), suggested.getIncrement());
    }

    @Test
    public void testIsNotVirtWhenFactIsEmpty() {
        consumer.setFact(IS_VIRT, "");
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testTotalConsumedIsZeroWhenNoMatches() {
        consumer.setFact(IS_VIRT, "");
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());

        Product product2 = TestUtil.createProduct();
        Pool pool2 = TestUtil.createPool(owner, product2);

        Entitlement e = createValidEntitlement(pool2);
        e.setQuantity(2);

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testCalculatedValueIsZeroWhenNegativeIsCalculated() {
        consumer.setFact(IS_VIRT, "");
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());

        Entitlement e = createValidEntitlement(pool);
        e.setQuantity(1000);

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(0), suggested.getSuggested());
    }

    @Test
    public void testTotalConsumedDoesNotIncludeFutureEntitlements() {
        consumer.setFact(IS_VIRT, "");
        consumer.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());

        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool, null);
        pool.setStartDate(TestUtil.createDate(9000, 1, 1));
        pool.setEndDate(TestUtil.createDate(9001, 1, 1));
        e.setQuantity(2);

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer,
                TestUtil.createDate(2010, 1, 1));
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testFutureSuggested() {
        consumer.setFact(SOCKET_FACT, "4");

        pool.setStartDate(TestUtil.createDate(9000, 1, 1));
        pool.setEndDate(TestUtil.createDate(9001, 1, 1));

        Pool currentPool = TestUtil.createPool(owner, product);
        currentPool.setStartDate(TestUtil.createDate(2000, 1, 1));
        currentPool.setEndDate(TestUtil.createDate(5000, 1, 1));
        currentPool.setProductAttribute("multi-entitlement", "yes", product.getId());
        currentPool.setProductAttribute("stacking_id", "1", product.getId());

        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        currentPool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());

        Entitlement currentEntitlement =
            TestUtil.createEntitlement(owner, consumer, currentPool, null);
        currentEntitlement.setQuantity(2);

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(currentEntitlement);
        consumer.setEntitlements(ents);

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(currentPool,
                consumer, TestUtil.createDate(2010, 6, 1));
        assertEquals(new Long(0), suggested.getSuggested());

        // Make sure current coverage does not affect the future
        suggested =
            quantityRules.getSuggestedQuantity(pool, consumer,
                TestUtil.createDate(9000, 6, 1));
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testPhysicalIgnoresFutureConsumed() {
        // Setup a future pool for the same product:
        Pool futurePool = TestUtil.createPool(owner, product);
        futurePool.setStartDate(TestUtil.createDate(2050, 1, 1));
        futurePool.setEndDate(TestUtil.createDate(2060, 1, 1));

        pool.setProductAttribute("multi-entitlement", "yes", product.getId());
        pool.setProductAttribute("stacking_id", "1", product.getId());
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "1", product.getId());
        futurePool.setProductAttribute("multi-entitlement", "yes", product.getId());
        futurePool.setProductAttribute("stacking_id", "1", product.getId());
        futurePool.setProductAttribute(SOCKET_ATTRIBUTE, "1", product.getId());

        consumer.setFact(SOCKET_FACT, "4");

        // Green in future but we have nothing now:
        Entitlement e = createValidEntitlement(futurePool);
        e.setQuantity(4);

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
    }

    @Test
    public void testPhysicalIgnoresPastConsumed() {
        pool.setProductAttribute("multi-entitlement", "yes", product.getId());
        pool.setProductAttribute("stacking_id", "1", product.getId());
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "1", product.getId());

        consumer.setFact(SOCKET_FACT, "4");

        // Green now, but we will ask for suggested quantity on a date in the future:
        Entitlement e = createValidEntitlement(pool);
        e.setQuantity(4);

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(e);

        consumer.setEntitlements(ents);

        // Ask for quantity in the future, past the end of the current pool:
        Calendar c = Calendar.getInstance();
        c.setTime(pool.getEndDate());
        Date futureDate = TestUtil.createDate(c.get(Calendar.YEAR) + 1, 1, 1);
        SuggestedQuantity suggested = quantityRules.getSuggestedQuantity(pool, consumer,
            futureDate);
        assertEquals(new Long(4), suggested.getSuggested());
    }

    @Test
    public void testStackOnlyStacksWithSameStackingId() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "8");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        pool.setQuantity(10L);
        Product product1 = TestUtil.createProduct();
        Pool pool1 = TestUtil.createPool(owner, product1);
        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool1,
            new EntitlementCertificate());

        Set<Entitlement> entSet = new HashSet<Entitlement>();
        entSet.add(e);
        pool1.setEntitlements(entSet);
        pool1.setProductAttribute("multi-entitlement", "yes", product1.getId());
        pool1.setProductAttribute("stacking_id", "2", product1.getId());
        pool1.setProductAttribute(SOCKET_ATTRIBUTE, "2", product1.getId());
        pool1.setQuantity(10L);

        // Consume 2 subscriptions with another stacking ID
        Entitlement toAdd = pool1.getEntitlements().iterator().next();
        toAdd.setQuantity(2);
        consumer.addEntitlement(toAdd);

        // Ensure the 2 attached entitlements do not cause the suggested quantity to change
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
    }

    /*
     * Guest limit should not have any bearing on the suggested quantity
     */
    @Test
    public void testInsufficientGuestLimit() {
        consumer.setFact(SOCKET_FACT, "8");
        Map<String, String> guestAttrs = new HashMap<String, String>();
        guestAttrs.put("virtWhoType", "libvirt");
        guestAttrs.put("active", "1");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, guestAttrs));
        }
        pool.setProductAttribute(GUEST_LIMIT_ATTRIBUTE, "4", product.getId());
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        pool.setQuantity(new Long(-1));
        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
    }

    /*
     * Distributors should always get suggested=1, increment=1
     */
    @Test
    public void testInstanceBasedOnDistributor() {
        Consumer dist = TestUtil.createConsumer(owner);
        dist.getType().setManifest(true);
        dist.setFact(IS_VIRT, "false");
        dist.setFact(SOCKET_FACT, "4");
        pool.setProductAttribute(SOCKET_ATTRIBUTE, "2", product.getId());
        pool.setProductAttribute(INSTANCE_ATTRIBUTE, "2", product.getId());

        SuggestedQuantity suggested =
            quantityRules.getSuggestedQuantity(pool, dist, new Date());
        assertEquals(new Long(1), suggested.getSuggested());
        assertEquals(new Long(1), suggested.getIncrement());
    }
}
