/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.bind.PoolOperations;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.SourceStack;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;



@ExtendWith(MockitoExtension.class)
public class PoolServiceTest {

    private static final String STACK = "a-stack";

    @Mock
    private PoolCurator poolCurator;
    @Mock
    private EventSink sink;
    @Mock
    private EventFactory eventFactory;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private EntitlementCertificateCurator entitlementCertCurator;
    @Mock
    private ComplianceRules complianceRules;
    @Mock
    private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock
    private I18n i18n;
    @Mock
    private PoolConverter poolConverter;
    private PoolService poolService;

    @BeforeEach
    void setUp() {
        DevConfig config = new DevConfig(Map.of(
            ConfigProperties.STANDALONE, "true",
            ConfigProperties.PRODUCT_CACHE_MAX, "100"));
        PoolRules poolRules = new PoolRules(config, entitlementCurator, poolConverter);
        poolService = new PoolService(poolCurator, sink, eventFactory, poolRules, entitlementCurator,
            consumerCurator, consumerTypeCurator, entitlementCertCurator, complianceRules,
            systemPurposeComplianceRules, config, i18n);
    }

    @Test
    public void virtLimitFromFirstVirtLimitEntBatch() {
        UserPrincipal principal = TestUtil.createOwnerPrincipal();
        Owner owner = principal.getOwners().get(0);

        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        Consumer consumer = new Consumer()
            .setName("consumer")
            .setUsername("registeredbybob")
            .setOwner(owner)
            .setType(ctype);

        // Two subtly different products stacked together:
        Product prod1 = TestUtil.createProduct("prod1", "prod1");
        prod1.setAttribute(Product.Attributes.VIRT_LIMIT, "2");
        prod1.setAttribute(Product.Attributes.STACKING_ID, STACK);
        prod1.setAttribute("testattr1", "1");

        Product prod2 = TestUtil.createProduct("prod2", "prod2");
        prod2.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        prod2.setAttribute(Product.Attributes.STACKING_ID, STACK);
        prod2.setAttribute("testattr2", "2");

        Product prod3 = TestUtil.createProduct("prod3", "prod3");
        prod3.setAttribute(Product.Attributes.VIRT_LIMIT, "9");
        prod3.setAttribute(Product.Attributes.STACKING_ID, STACK + "3");
        prod3.setAttribute("testattr2", "2");

        // Engineering (provided) products:
        Product provided1 = TestUtil.createProduct();
        Product provided2 = TestUtil.createProduct();
        Product provided3 = TestUtil.createProduct();
        Product provided4 = TestUtil.createProduct();

        prod1.addProvidedProduct(provided1);
        prod2.addProvidedProduct(provided2);
        prod3.addProvidedProduct(provided3);

        // Create three subscriptions with various start/end dates:
        Subscription sub1 = createStackedVirtSub(owner, prod1, TestUtil.createDate(2010, 1, 1),
            TestUtil.createDate(2015, 1, 1));
        Pool pool1 = copyFromSub(sub1, 1);

        Subscription sub2 = createStackedVirtSub(owner, prod2, TestUtil.createDate(2011, 1, 1),
            TestUtil.createDate(2017, 1, 1));

        Pool pool2 = copyFromSub(sub2, 2);
        prod2.addProvidedProduct(provided3);
        Subscription sub3 = createStackedVirtSub(owner, prod2, TestUtil.createDate(2012, 1, 1),
            TestUtil.createDate(2020, 1, 1));
        Pool pool3 = copyFromSub(sub3, 3);

        Subscription sub4 = createStackedVirtSub(owner, prod3, TestUtil.createDate(2012, 1, 1),
            TestUtil.createDate(2020, 1, 1));
        Pool pool4 = copyFromSub(sub4, 3);

        List<Entitlement> stackedEnts = new LinkedList<>();
        // Initial entitlement from one of the pools:
        stackedEnts.add(createEntFromPool(owner, pool2, consumer));

        pool2.setAttribute(Product.Attributes.VIRT_LIMIT, "60");
        pool4.setAttribute(Product.Attributes.VIRT_LIMIT, "80");

        List<Pool> reqPools = new ArrayList<>();
        reqPools.add(pool2);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool2.getId(), stackedEnts.get(0));
        Map<String, Map<String, String>> attributes = new HashMap<>();
        attributes.put(pool2.getId(), PoolHelper.getFlattenedAttributes(pool2));
        PoolOperations poolOperations1 = PoolHelper.createHostRestrictedPools(poolService,
            consumer, reqPools, entitlements, attributes);
        Pool stackDerivedPool1 = poolOperations1.creations().get(0);

        reqPools.clear();
        reqPools.add(pool4);
        entitlements.clear();
        entitlements.put(pool4.getId(), createEntFromPool(owner, pool4, consumer));
        attributes.clear();
        attributes.put(pool4.getId(), PoolHelper.getFlattenedAttributes(pool4));
        PoolOperations poolOperations2 = PoolHelper.createHostRestrictedPools(
            poolService, consumer, reqPools, entitlements, attributes);
        Pool stackDerivedPool2 = poolOperations2.creations().get(0);

        stackedEnts.clear();
        Entitlement e1 = createEntFromPool(owner, pool1, consumer);
        e1.setQuantity(4);
        stackedEnts.add(e1);
        Entitlement e2 = createEntFromPool(owner, pool4, consumer);
        e2.setQuantity(16);
        stackedEnts.add(e2);
        Class<Set<String>> listClass = (Class<Set<String>>) (Class) HashSet.class;
        ArgumentCaptor<Set<String>> arg = ArgumentCaptor.forClass(listClass);

        when(entitlementCurator.findByStackIds(eq(consumer), arg.capture())).thenReturn(stackedEnts);

        List<PoolUpdate> updates = poolService.updatePoolsFromStack(consumer,
            Arrays.asList(stackDerivedPool1, stackDerivedPool2), null, false);
        Set<String> stackIds = arg.getValue();
        assertEquals(2, stackIds.size());
        assertThat(stackIds, hasItems(STACK, STACK + "3"));

        for (PoolUpdate update : updates) {
            assertTrue(update.changed());
            assertTrue(update.getQuantityChanged());
        }
        assertEquals((Long) 2L, stackDerivedPool1.getQuantity());
        assertEquals((Long) 9L, stackDerivedPool2.getQuantity());
    }

    private Pool copyFromSub(Subscription sub, int id) {
        Pool pool = TestUtil.copyFromSub(sub);
        pool.setId("pool_" + id);
        return pool;
    }

    private Subscription createStackedVirtSub(Owner owner, Product product, Date start, Date end) {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.setStartDate(start);
        s.setEndDate(end);
        s.setProduct(product.toDTO());
        s.setContractNumber(Integer.toString(TestUtil.randomInt()));
        s.setOrderNumber(Integer.toString(TestUtil.randomInt()));
        s.setAccountNumber(Integer.toString(TestUtil.randomInt()));
        return s;
    }

    @Test
    public void bulkUpdateDoesNotDeletesPoolsWithoutStackingEntitlements() {
        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        Set<Consumer> consumers = Collections.singleton(consumer);
        List<Pool> pools = createPools(owner);
        List<Entitlement> stackingEntitlements = createEntitlements(owner, consumer, pools);
        when(entitlementCurator.findByStackIds(isNull(), anyCollection()))
            .thenReturn(stackingEntitlements);

        poolService.bulkUpdatePoolsFromStack(consumers, pools, new ArrayList<>(), false);

        verifyNoInteractions(poolCurator);
    }

    @Test
    public void bulkUpdateDeletesPoolsWithoutStackingEntitlements() {
        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        Set<Consumer> consumers = Collections.singleton(consumer);
        List<Pool> pools = createPools(owner);
        List<Entitlement> stackingEntitlements = createEntitlements(owner, consumer, pools);
        pools.add(createPool(owner));
        when(entitlementCurator.findByStackIds(isNull(), anyCollection()))
            .thenReturn(stackingEntitlements);
        when(poolCurator.lockAndLoad(anyCollection())).thenReturn(pools);

        poolService.bulkUpdatePoolsFromStack(consumers, pools, new ArrayList<>(), true);

        verify(poolCurator).batchDelete(anyCollection(), anyCollection());
    }

    @Test
    public void testNullArgumentsDontBreakStuff() {
        Owner owner = TestUtil.createOwner();

        poolService.getBySubscriptionIds(owner.getId(), null);
        poolService.getBySubscriptionIds(owner.getId(), new ArrayList<>());
        poolService.getBySubscriptionId(owner, null);
    }

    @Test
    public void testGetBySubscriptionIds() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct();
        List<Pool> pools = new ArrayList<>();
        List<String> subids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pools.add(TestUtil.createPool(owner, product));
            pools.get(i).setId("id" + i);
            subids.add("subid" + i);
        }

        Class<List<String>> listClass = (Class<List<String>>) (Class) ArrayList.class;
        ArgumentCaptor<List<String>> poolsArg = ArgumentCaptor.forClass(listClass);
        when(poolCurator.getBySubscriptionIds(anyString(), poolsArg.capture()))
            .thenReturn(pools);
        List<Pool> found = poolService.getBySubscriptionIds(owner.getId(), subids);
        List<String> argument = poolsArg.getValue();
        assertEquals(pools, found);
        assertEquals(argument, subids);
    }

    private Entitlement createEntFromPool(Owner owner, Pool pool, Consumer consumer) {
        Entitlement e = new Entitlement(pool, consumer, owner, 2);
        e.setCreated(new Date());
        try {
            Thread.sleep(1);
        }
        catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        return e;
    }

    private List<Entitlement> createEntitlements(Owner owner, Consumer consumer, List<Pool> pools) {
        return pools.stream()
            .map(pool -> createEntitlement(owner, pool, consumer))
            .collect(Collectors.toList());
    }

    private List<Pool> createPools(Owner owner) {
        ArrayList<Pool> pools = new ArrayList<>();
        pools.add(createStackingPool(owner));
        pools.add(createStackingPool(owner));
        pools.add(createStackingPool(owner));
        return pools;
    }

    private Pool createPool(Owner owner) {
        return TestUtil.createPool(owner, TestUtil.createProduct());
    }

    private Entitlement createEntitlement(Owner owner, Pool pool, Consumer consumer) {
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        entitlement.setCreated(new Date());
        return entitlement;
    }

    private Pool createStackingPool(Owner owner) {
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.STACKING_ID, "RH001");
        Pool pool = TestUtil.createPool(owner, product);
        SourceStack sourceStack = new SourceStack();
        sourceStack.setSourceStackId("RH001");
        pool.setSourceStack(sourceStack);
        return pool;
    }

}
