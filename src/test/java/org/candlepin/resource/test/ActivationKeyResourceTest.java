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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.activationkeys.ActivationKeyPool;
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

    @Before
    public void setUp() {

        activationKeyResource = injector
            .getInstance(ActivationKeyResource.class);
    }

    @Test(expected = BadRequestException.class)
    public void testCreateReadDelete() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        key.setOwner(owner);
        key.setName("dd");
        key.setServiceLevel("level");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());
        key = activationKeyResource.getActivationKey(key.getId());
        assertNotNull(key);
        key.setName("JarJarBinks");
        key = activationKeyResource.updateActivationKey(key.getId(), key);
        key = activationKeyResource.getActivationKey(key.getId());
        assertEquals("JarJarBinks", key.getName());
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
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = mock(Pool.class);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        akr.addPoolToKey("testKey", "testPool", 2L);
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithNegPoolQuantity() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = mock(Pool.class);
        PoolManager poolManager = mock(PoolManager.class);
        ProductPoolAttribute ppa = mock(ProductPoolAttribute.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);
        when(p.getProductAttribute(eq("multi-entitlement"))).thenReturn(ppa);
        when(ppa.getValue()).thenReturn("yes");
        when(p.getQuantity()).thenReturn(10L);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        akr.addPoolToKey("testKey", "testPool", -3L);
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithLargePoolQuantity() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = mock(Pool.class);
        PoolManager poolManager = mock(PoolManager.class);
        ProductPoolAttribute ppa = mock(ProductPoolAttribute.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);
        when(p.getProductAttribute(eq("multi-entitlement"))).thenReturn(ppa);
        when(ppa.getValue()).thenReturn("yes");
        when(p.getQuantity()).thenReturn(10L);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        akr.addPoolToKey("testKey", "testPool", 15L);
    }

    @Test
    public void testActivationKeyWithUnlimitedPool() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = mock(Pool.class);
        PoolManager poolManager = mock(PoolManager.class);
        ProductPoolAttribute ppa = mock(ProductPoolAttribute.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);
        when(p.isUnlimited()).thenReturn(true);
        when(p.getProductAttribute(eq("multi-entitlement"))).thenReturn(ppa);
        when(ppa.getValue()).thenReturn("yes");

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        akr.addPoolToKey("testKey", "testPool", 15L);
    }


    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithPersonConsumerType() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = mock(Pool.class);
        PoolManager poolManager = mock(PoolManager.class);
        ProductPoolAttribute ppa = mock(ProductPoolAttribute.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);
        when(p.getProductAttribute(eq("requires_consumer_type"))).thenReturn(ppa);
        when(ppa.getValue()).thenReturn("person");
        when(p.getQuantity()).thenReturn(1L);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        akr.addPoolToKey("testKey", "testPool", 1L);
    }

    @Test
    public void testActivationKeyWithNonPersonConsumerType() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = mock(Pool.class);
        PoolManager poolManager = mock(PoolManager.class);
        ProductPoolAttribute ppa = mock(ProductPoolAttribute.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);
        when(p.getProductAttribute(eq("requires_consumer_type"))).thenReturn(ppa);
        when(ppa.getValue()).thenReturn("candlepin");
        when(p.getQuantity()).thenReturn(1L);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        assertNotNull(akr.addPoolToKey("testKey", "testPool", 1L));
    }

    @Test
    public void testActivationKeyWithSameHostReqPools() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = mock(Pool.class);
        Pool p2 = mock(Pool.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool1"))).thenReturn(p1);
        when(poolManager.find(eq("testPool2"))).thenReturn(p2);
        when(p1.getAttributeValue(eq("requires_host"))).thenReturn("host1");
        when(p2.getAttributeValue(eq("requires_host"))).thenReturn("host1");
        when(p1.getQuantity()).thenReturn(1L);
        when(p2.getQuantity()).thenReturn(1L);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        when(ak.getPools()).thenReturn(new HashSet<ActivationKeyPool>());
        akr.addPoolToKey("testKey", "testPool1", 1L);
        verify(ak).addPool(eq(p1), eq(1L));
        Set<ActivationKeyPool> akPools = new HashSet<ActivationKeyPool>();
        akPools.add(new ActivationKeyPool(ak, p1, 1L));
        when(ak.getPools()).thenReturn(akPools);
        akr.addPoolToKey("testKey", "testPool2", 1L);
        verify(ak).addPool(eq(p2), eq(1L));
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyWithDiffHostReqPools() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = mock(Pool.class);
        Pool p2 = mock(Pool.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool1"))).thenReturn(p1);
        when(p1.getAttributeValue(eq("requires_host"))).thenReturn("host1");
        when(p1.getQuantity()).thenReturn(1L);
        when(p2.getAttributeValue(eq("requires_host"))).thenReturn("host2");
        when(p2.getQuantity()).thenReturn(1L);
        Set<ActivationKeyPool> akPools = new HashSet<ActivationKeyPool>();
        akPools.add(new ActivationKeyPool(ak, p2, 1L));
        when(ak.getPools()).thenReturn(akPools);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        akr.addPoolToKey("testKey", "testPool1", 1L);
    }

    @Test
    public void testActivationKeyHostReqPoolThenNonHostReq() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = mock(Pool.class);
        Pool p2 = mock(Pool.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool1"))).thenReturn(p1);
        when(poolManager.find(eq("testPool2"))).thenReturn(p2);
        when(p1.getAttributeValue(eq("requires_host"))).thenReturn("host1");
        when(p2.getAttributeValue(eq("requires_host"))).thenReturn("");
        when(p1.getQuantity()).thenReturn(1L);
        when(p2.getQuantity()).thenReturn(1L);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        when(ak.getPools()).thenReturn(new HashSet<ActivationKeyPool>());
        akr.addPoolToKey("testKey", "testPool1", 1L);
        verify(ak).addPool(eq(p1), eq(1L));
        Set<ActivationKeyPool> akPools = new HashSet<ActivationKeyPool>();
        akPools.add(new ActivationKeyPool(ak, p1, 1L));
        when(ak.getPools()).thenReturn(akPools);
        akr.addPoolToKey("testKey", "testPool2", 1L);
        verify(ak).addPool(eq(p2), eq(1L));
    }

    @Test
    public void testActivationKeyWithNullQuantity() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = mock(Pool.class);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.verifyAndLookupKey(eq("testKey"))).thenReturn(ak);
        when(poolManager.find(eq("testPool"))).thenReturn(p);

        ActivationKeyResource akr = new ActivationKeyResource(
            akc, i18n, poolManager, serviceLevelValidator);
        akr.addPoolToKey("testKey", "testPool", null);
    }
}
