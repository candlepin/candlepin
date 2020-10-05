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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.rules.v1.SuggestedQuantityDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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
    private static final String VCPU_ATTRIBUTE = "vcpu";
    private static final String CORES_FACT = "cpu.core(s)_per_socket";
    private static final String IS_VIRT = "virt.is_guest";
    private static final String GUEST_LIMIT_ATTRIBUTE = "guest_limit";

    private Consumer consumer;
    private ConsumerType ctype;
    private Pool pool;
    private Product product;
    private Owner owner;
    private QuantityRules quantityRules;
    private JsRunnerProvider provider;
    private ModelTranslator translator;

    @Mock private RulesCurator rulesCuratorMock;
    @Mock private OwnerCurator ownerCuratorMock;
    @Mock private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock private JsRunnerRequestCache cache;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private EnvironmentCurator environmentCurator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        when(cacheProvider.get()).thenReturn(cache);
        provider = new JsRunnerProvider(rulesCuratorMock, cacheProvider);

        translator = new StandardTranslator(consumerTypeCurator, environmentCurator, ownerCuratorMock);
        quantityRules = new QuantityRules(provider.get(), new RulesObjectMapper(), translator);

        owner = TestUtil.createOwner();
        product = TestUtil.createProduct();
        pool = TestUtil.createPool(owner, product);
        pool.setId("fakepoolid");

        ctype = TestUtil.createConsumerType();
        consumer = TestUtil.createConsumer(owner);
        when(consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(consumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool, new EntitlementCertificate());

        Set<Entitlement> entSet = new HashSet<>();
        entSet.add(e);

        pool.setEntitlements(entSet);
        pool.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        pool.getProduct().setAttribute(Product.Attributes.STACKING_ID, "1");
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
        pool.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "no");
        SuggestedQuantityDTO suggested = quantityRules.getSuggestedQuantity(pool,
            TestUtil.createConsumer(), new Date());
        assertEquals(new Long(1), suggested.getSuggested());
    }

    @Test
    public void testNonMultiEntitlementPoolMultiPool() {
        pool.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "no");
        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        Map<String, SuggestedQuantityDTO> results = quantityRules.getSuggestedQuantities(
            pools, TestUtil.createConsumer(), new Date());
        assertTrue(results.containsKey(pool.getId()));
        SuggestedQuantityDTO suggested = results.get(pool.getId());
        assertEquals(new Long(1), suggested.getSuggested());
    }

    @Test
    public void testPhysicalDefaultToNumSocketsBySocketCount() {
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testPhysicalRoundsUp() {
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "3");
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testPhysicalAccountsForCurrentlyConsumed() {
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "1");

        Entitlement e = createValidEntitlement(pool);
        e.setQuantity(2);

        Set<Entitlement> ents = new HashSet<>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testVirtDefaultToNumCpusByVcpuCount() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(CORES_FACT, "8");
        pool.getProduct().setAttribute(VCPU_ATTRIBUTE, "4");
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void shouldUseCoresIfNoVcpu() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(SOCKET_FACT, "1");
        consumer.setFact(CORES_FACT, "8");
        pool.getProduct().setAttribute(CORES_ATTRIBUTE, "2");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "1");

        final SuggestedQuantityDTO suggested = quantityRules
            .getSuggestedQuantity(pool, consumer, new Date());

        assertEquals(new Long(4), suggested.getSuggested());
    }

    @Test
    public void testVirtIgnoresSockets() {
        // Ensure that we start this test with no entitlements.
        consumer.getEntitlements().clear();
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(1), suggested.getSuggested());
    }

    @Test
    public void testVirtUses1IfNoVcpu() {
        // Ensure that we start this test with no entitlements.
        consumer.getEntitlements().clear();
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(SOCKET_FACT, "4");
        consumer.setFact(CORES_FACT, "8");
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(1), suggested.getSuggested());
    }

    @Test
    public void testVirtRoundsUp() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(CORES_FACT, "8");
        pool.getProduct().setAttribute(VCPU_ATTRIBUTE, "6");
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testVirtAccountsForCurrentlyConsumed() {
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(CORES_FACT, "4");
        pool.getProduct().setAttribute(VCPU_ATTRIBUTE, "1");

        Entitlement e = createValidEntitlement(pool);
        e.setQuantity(2);

        Set<Entitlement> ents = new HashSet<>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testUnlimitedQuantity() {
        consumer.setFact(SOCKET_FACT, "8");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.setQuantity(new Long(-1));
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
    }

    @Test
    public void testIsNotVirtWhenFactIsFalse() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
        assertEquals(new Long(1), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnPhysical() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.getProduct().setAttribute(INSTANCE_ATTRIBUTE, "2");

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
        assertEquals(new Long(2), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnSingleSocketPhysical() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "1");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.getProduct().setAttribute(INSTANCE_ATTRIBUTE, "2");

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
        assertEquals(new Long(2), suggested.getIncrement());
    }

    @Test
    public void testSingleSocketInstanceBasedOnPhysical() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "1");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "1");
        pool.getProduct().setAttribute(INSTANCE_ATTRIBUTE, "2");

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
        assertEquals(new Long(2), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnPhysicalNotEnoughAvailable() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "40"); // lots of ents required
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.getProduct().setAttribute(INSTANCE_ATTRIBUTE, "2");

        pool.setQuantity(4L);
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
        assertEquals(new Long(2), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnPhysicalNotEnoughAvailableUneven() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "40"); // lots of ents required
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.getProduct().setAttribute(INSTANCE_ATTRIBUTE, "2");

        pool.setQuantity(3L);
        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
        assertEquals(new Long(2), suggested.getIncrement());
    }

    @Test
    public void testInstanceBasedOnGuest() {
        // Ensure that we start this test with no entitlements.
        consumer.getEntitlements().clear();
        consumer.setFact(IS_VIRT, "true");
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.getProduct().setAttribute(INSTANCE_ATTRIBUTE, "2");

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(1), suggested.getSuggested());
        assertEquals(new Long(1), suggested.getIncrement());
    }

    @Test
    public void testIsNotVirtWhenFactIsEmpty() {
        consumer.setFact(IS_VIRT, "");
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testTotalConsumedIsZeroWhenNoMatches() {
        consumer.setFact(IS_VIRT, "");
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");

        Product product2 = TestUtil.createProduct();
        Pool pool2 = TestUtil.createPool(owner, product2);

        Entitlement e = createValidEntitlement(pool2);
        e.setQuantity(2);

        Set<Entitlement> ents = new HashSet<>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(2), suggested.getSuggested());
    }

    @Test
    public void testCalculatedValueIsZeroWhenNegativeIsCalculated() {
        consumer.setFact(IS_VIRT, "");
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");

        Entitlement e = createValidEntitlement(pool);
        e.setQuantity(1000);

        Set<Entitlement> ents = new HashSet<>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(0), suggested.getSuggested());
    }

    @Test
    public void testTotalConsumedDoesNotIncludeFutureEntitlements() {
        consumer.setFact(IS_VIRT, "");
        consumer.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");

        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool, null);
        pool.setStartDate(TestUtil.createDate(9000, 1, 1));
        pool.setEndDate(TestUtil.createDate(9001, 1, 1));
        e.setQuantity(2);

        Set<Entitlement> ents = new HashSet<>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantityDTO suggested = quantityRules.getSuggestedQuantity(
            pool, consumer, TestUtil.createDate(2010, 1, 1));
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
        currentPool.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        currentPool.getProduct().setAttribute(Product.Attributes.STACKING_ID, "1");

        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        currentPool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");

        Entitlement currentEntitlement =
            TestUtil.createEntitlement(owner, consumer, currentPool, null);
        currentEntitlement.setQuantity(2);

        Set<Entitlement> ents = new HashSet<>();
        ents.add(currentEntitlement);
        consumer.setEntitlements(ents);

        SuggestedQuantityDTO suggested = quantityRules.getSuggestedQuantity(
            currentPool, consumer, TestUtil.createDate(2010, 6, 1));
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

        pool.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        pool.getProduct().setAttribute(Product.Attributes.STACKING_ID, "1");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "1");
        futurePool.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        futurePool.getProduct().setAttribute(Product.Attributes.STACKING_ID, "1");
        futurePool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "1");

        consumer.setFact(SOCKET_FACT, "4");

        // Green in future but we have nothing now:
        Entitlement e = createValidEntitlement(futurePool);
        e.setQuantity(4);

        Set<Entitlement> ents = new HashSet<>();
        ents.add(e);

        consumer.setEntitlements(ents);

        SuggestedQuantityDTO suggested =
            quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
    }

    @Test
    public void testPhysicalIgnoresPastConsumed() {
        pool.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        pool.getProduct().setAttribute(Product.Attributes.STACKING_ID, "1");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "1");

        consumer.setFact(SOCKET_FACT, "4");

        // Green now, but we will ask for suggested quantity on a date in the future:
        Entitlement e = createValidEntitlement(pool);
        e.setQuantity(4);

        Set<Entitlement> ents = new HashSet<>();
        ents.add(e);

        consumer.setEntitlements(ents);

        // Ask for quantity in the future, past the end of the current pool:
        Calendar c = Calendar.getInstance();
        c.setTime(pool.getEndDate());
        Date futureDate = TestUtil.createDate(c.get(Calendar.YEAR) + 1, 1, 1);
        SuggestedQuantityDTO suggested = quantityRules.getSuggestedQuantity(pool, consumer, futureDate);
        assertEquals(new Long(4), suggested.getSuggested());
    }

    @Test
    public void testStackOnlyStacksWithSameStackingId() {
        consumer.setFact(IS_VIRT, "false");
        consumer.setFact(SOCKET_FACT, "8");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.setQuantity(10L);
        Product product1 = TestUtil.createProduct();
        Pool pool1 = TestUtil.createPool(owner, product1);
        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool1,
            new EntitlementCertificate());

        Set<Entitlement> entSet = new HashSet<>();
        entSet.add(e);
        pool1.setEntitlements(entSet);
        pool1.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        pool1.getProduct().setAttribute(Product.Attributes.STACKING_ID, "2");
        pool1.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool1.setQuantity(10L);

        // Consume 2 subscriptions with another stacking ID
        Entitlement toAdd = pool1.getEntitlements().iterator().next();
        toAdd.setQuantity(2);
        consumer.addEntitlement(toAdd);

        // Ensure the 2 attached entitlements do not cause the suggested quantity to change
        SuggestedQuantityDTO suggested = quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
    }

    /*
     * Guest limit should not have any bearing on the suggested quantity
     */
    @Test
    public void testInsufficientGuestLimit() {
        consumer.setFact(SOCKET_FACT, "8");
        Map<String, String> guestAttrs = new HashMap<>();
        guestAttrs.put("virtWhoType", "libvirt");
        guestAttrs.put("active", "1");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, guestAttrs));
        }

        pool.getProduct().setAttribute(GUEST_LIMIT_ATTRIBUTE, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.setQuantity(new Long(-1));
        SuggestedQuantityDTO suggested = quantityRules.getSuggestedQuantity(pool, consumer, new Date());
        assertEquals(new Long(4), suggested.getSuggested());
    }

    /*
     * Distributors should always get suggested=1, increment=1
     */
    @Test
    public void testInstanceBasedOnDistributor() {
        Consumer dist = TestUtil.createConsumer(owner);
        dist.setFact(IS_VIRT, "false");
        dist.setFact(SOCKET_FACT, "4");
        pool.getProduct().setAttribute(SOCKET_ATTRIBUTE, "2");
        pool.getProduct().setAttribute(INSTANCE_ATTRIBUTE, "2");

        ctype.setManifest(true);
        when(consumerTypeCurator.getConsumerType(eq(dist))).thenReturn(ctype);

        SuggestedQuantityDTO suggested = quantityRules.getSuggestedQuantity(pool, dist, new Date());
        assertEquals(new Long(1), suggested.getSuggested());
        assertEquals(new Long(1), suggested.getIncrement());
    }
}
