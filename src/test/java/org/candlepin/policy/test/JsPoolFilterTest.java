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

import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.PoolFilter;
import org.candlepin.policy.js.JsRulesProvider;
import org.candlepin.policy.js.pool.JsPoolFilter;
import org.candlepin.policy.js.pool.JsPoolRules;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.Config;
import org.candlepin.controller.PoolManager;

/**
 * JsPoolFilter
 */
@RunWith(MockitoJUnitRunner.class)
public class JsPoolFilterTest {
    private static final String RULES_FILE = "/rules/default-rules.js";
    private JsRulesProvider provider;

    @Mock private RulesCurator rulesCuratorMock;
    @Mock private ProductServiceAdapter productAdapterMock;
    @Mock private PoolManager poolManagerMock;
    @Mock private Config configMock;
    @Mock private ConsumerCurator consumerCuratorMock;

    private UserPrincipal principal;
    private Owner owner;
    private Consumer consumer;
    private PoolFilter poolFilter;
    private JsPoolRules poolRules;

    @Before
    public void setUp() {

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);


        this.provider = new JsRulesProvider(rulesCuratorMock);
        poolRules = new JsPoolRules(this.provider.get(), poolManagerMock,
                                    productAdapterMock, configMock);

        poolFilter = new JsPoolFilter(this.provider.get(), configMock, consumerCuratorMock);
        principal = TestUtil.createOwnerPrincipal();
        owner = principal.getOwners().get(0);

    }


    @Test
    public void poolFilter() {
        consumer = TestUtil.createConsumer(owner);
        Product targetProduct = TestUtil.createProduct();
        Pool targetPool = TestUtil.createPool(targetProduct);
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(targetPool);

        poolFilter.filterPools(consumer, pools);
        assertFalse(pools.isEmpty());

    }

    @Test
    public void poolFilterConsumerIsGuest() {
        consumer = TestUtil.createConsumer(owner);
        consumer.addGuestId(new GuestId());
        consumer.setFact("virt.is_guest", "true");

        Product targetProduct = TestUtil.createProduct();
        Pool targetPool = TestUtil.createPool(targetProduct);
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(targetPool);

        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
        assertEquals(pools, newPools);
    }

    @Test
    public void poolFilterConsumerIsNotGuestVirtOnlyPools() {
        consumer = TestUtil.createConsumer(owner);
        consumer.addGuestId(new GuestId());

        Product targetProduct = TestUtil.createProduct();
        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setAttribute("virt_only", "true");
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(targetPool);

        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
        assertTrue(newPools.isEmpty());
    }

    @Test
    public void poolFilterConsumerIsGuestVirtOnlyPools() {
        consumer = TestUtil.createConsumer(owner);
        consumer.addGuestId(new GuestId());
        consumer.setFact("virt.is_guest", "true");

        Product targetProduct = TestUtil.createProduct();
        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setAttribute("virt_only", "true");
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(targetPool);

        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
        assertEquals(pools, newPools);
    }

    @Test
    public void poolFilterConsumerIsGuestVirtOnlyPoolsMatch() {
        consumer = TestUtil.createConsumer(owner);
        consumer.setFact("virt.is_guest", "true");
        String guestId = "1234567";
        consumer.addGuestId(new GuestId(guestId));
        when(consumerCuratorMock.getHost(guestId)).thenReturn(consumer);

        poolFilter = new JsPoolFilter(this.provider.get(), configMock, consumerCuratorMock);

        Product targetProduct = TestUtil.createProduct();
        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setAttribute("virt_only", "true");
        targetPool.setAttribute("requires_host", guestId);
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(targetPool);

        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
        assertEquals(pools, newPools);
    }

    @Test
    public void poolFilterConsumerIsGuestVirtOnlyPoolsNoMatch() {
        consumer = TestUtil.createConsumer(owner);
        consumer.setFact("virt.is_guest", "true");
        String guestId = "1234567";
        String notAValidGuestId = "thisisnotaguestid";
        consumer.addGuestId(new GuestId(guestId));

        when(consumerCuratorMock.getHost(notAValidGuestId)).thenReturn(null);
        poolFilter = new JsPoolFilter(this.provider.get(), configMock, consumerCuratorMock);

        Product targetProduct = TestUtil.createProduct();
        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setAttribute("virt_only", "true");
        targetPool.setAttribute("requires_host", notAValidGuestId);
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(targetPool);

        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
        assertTrue(newPools.isEmpty());
    }

}
