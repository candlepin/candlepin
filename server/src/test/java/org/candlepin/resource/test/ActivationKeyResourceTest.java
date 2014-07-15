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
package org.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.resource.ActivationKeyResource;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
/**
 * SubscriptionTokenResourceTest
 */
public class ActivationKeyResourceTest extends DatabaseTestFixture {
    protected ActivationKeyResource activationKeyResource;
    protected ActivationKeyRules activationKeyRules;
    private static int poolid = 0;

    @Before
    public void setUp() {

        activationKeyResource = injector
            .getInstance(ActivationKeyResource.class);
        activationKeyRules = injector
            .getInstance(ActivationKeyRules.class);
    }

    @Test(expected = BadRequestException.class)
    public void testCreateReadDelete() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        key.setOwner(owner);
        key.setName("dd");
        key.setServiceLevel("level1");
        key.setReleaseVer(new Release("release1"));
        activationKeyCurator.create(key);

        assertNotNull(key.getId());
        key = activationKeyResource.getActivationKey(key.getId());
        assertNotNull(key);
        key.setName("JarJarBinks");
        key.setServiceLevel("level2");
        key.setReleaseVer(new Release("release2"));
        key = activationKeyResource.updateActivationKey(key.getId(), key);
        key = activationKeyResource.getActivationKey(key.getId());
        assertEquals("JarJarBinks", key.getName());
        assertEquals("level2", key.getServiceLevel());
        assertEquals("release2", key.getReleaseVer().getReleaseVer());
        activationKeyResource.deleteActivationKey(key.getId());
        key = activationKeyResource.getActivationKey(key.getId());
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidTokenIdOnDelete() {
        activationKeyResource.deleteActivationKey("JarJarBinks");
    }

    @Test
    public void testAddingRemovingPools() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        Product product = TestUtil.createProduct();
        productCurator.create(product);
        Pool pool = createPoolAndSub(owner, product, 10L, new Date(), new Date());
        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);
        assertNotNull(key.getId());
        activationKeyResource.addPoolToKey(key.getId(), pool.getId(), 1L);
//        key = activationKeyResource.createActivationKey(key);
        assertTrue(key.getPools().size() == 1);
        activationKeyResource.removePoolFromKey(key.getId(), pool.getId());
//        key = activationKeyResource.createActivationKey(key);
        assertTrue(key.getPools().size() == 0);
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithNonMultiPool() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.setProductAttribute("multi-entitlement", "no", "id");
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        akr.addPoolToKey("testKey", "testPool", 2L);
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithNegPoolQuantity() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.setQuantity(10L);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        akr.addPoolToKey("testKey", "testPool", -3L);
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithLargePoolQuantity() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.setProductAttribute("multi-entitlement", "yes", "id");
        p.setQuantity(10L);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        akr.addPoolToKey("testKey", "testPool", 15L);
    }

    @Test
    public void testActivationKeyWithUnlimitedPool() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.setProductAttribute("multi-entitlement", "yes", "id");
        p.setQuantity(-1L);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        akr.addPoolToKey("testKey", "testPool", 15L);
    }


    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithPersonConsumerType() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.setProductAttribute("requires_consumer_type", "person", "id");
        p.setQuantity(1L);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        akr.addPoolToKey("testKey", "testPool", 1L);
    }

    @Test
    public void testActivationKeyWithNonPersonConsumerType() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.setProductAttribute("requires_consumer_type", "candlepin", "id");
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        assertNotNull(akr.addPoolToKey("testKey", "testPool", 1L));
    }

    @Test
    public void testActivationKeyWithSameHostReqPools() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = genPool();
        p1.setAttribute("requires_host", "host1");
        Pool p2 = genPool();
        p2.setAttribute("requires_host", "host1");

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool1"))).thenReturn(p1);
        when(poolManager.find(eq("testPool2"))).thenReturn(p2);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);

        akr.addPoolToKey("testKey", "testPool1", 1L);
        assertEquals(1, ak.getPools().size());
        Set<ActivationKeyPool> akPools = new HashSet<ActivationKeyPool>();
        akPools.add(new ActivationKeyPool(ak, p1, 1L));
        ak.setPools(akPools);
        akr.addPoolToKey("testKey", "testPool2", 1L);
        assertEquals(2, ak.getPools().size());
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithDiffHostReqPools() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = genPool();
        p1.setAttribute("requires_host", "host1");
        Pool p2 = genPool();
        p2.setAttribute("requires_host", "host2");

        ak.addPool(p2, 1L);
        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool1"))).thenReturn(p1);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        akr.addPoolToKey("testKey", "testPool1", 1L);
    }

    @Test
    public void testActivationKeyHostReqPoolThenNonHostReq() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = genPool();
        p1.setAttribute("requires_host", "host1");
        Pool p2 = genPool();
        p2.setAttribute("requires_host", "");

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool1"))).thenReturn(p1);
        when(poolManager.find(eq("testPool2"))).thenReturn(p2);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        akr.addPoolToKey("testKey", "testPool1", 1L);
        assertEquals(1, ak.getPools().size());
        ak.addPool(p1, 1L);
        akr.addPoolToKey("testKey", "testPool2", 1L);
        assertEquals(3, ak.getPools().size());
    }

    @Test
    public void testActivationKeyWithNullQuantity() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules);
        akr.addPoolToKey("testKey", "testPool", null);
    }

    @Test(expected = BadRequestException.class)
    public void testUpdateTooLongRelease() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        key.setOwner(owner);
        key.setName("dd");
        key.setServiceLevel("level1");
        key.setReleaseVer(new Release("release1"));
        activationKeyCurator.create(key);

        ActivationKey key2 = new ActivationKey();
        key2.setOwner(owner);
        key2.setName("dd");
        key2.setServiceLevel("level1");
        key2.setReleaseVer(new Release(TestUtil.getStringOfSize(256)));
        key = activationKeyResource.updateActivationKey(key.getId(), key2);
    }

    private Pool genPool() {
        Pool pool = new Pool();
        pool.setId("" + poolid++);
        pool.setQuantity(10L);
        pool.setConsumed(4L);
        pool.setAttribute("multi-entitlement", "yes");
        return pool;
    }

    private ActivationKey genActivationKey() {
        return new ActivationKey();
    }
}
