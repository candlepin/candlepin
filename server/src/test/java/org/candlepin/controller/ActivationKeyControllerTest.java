/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ServiceLevelValidator;

import com.google.inject.Injector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ActivationKeyResourceTest
 */
public class ActivationKeyControllerTest extends DatabaseTestFixture {
    @Inject private OwnerProductCurator ownerProductCurator;
    @Inject private ActivationKeyCurator activationKeyCurator;
    @Inject private ServiceLevelValidator serviceLevelValidator;
    @Inject private I18n i18n;
    @Inject private Injector injector;
    @Inject private PoolManager poolManager;

    private ActivationKeyRules activationKeyRules;
    private static int poolid = 0;
    private Owner owner;

    @BeforeEach
    public void setUp() {
        activationKeyRules = injector.getInstance(ActivationKeyRules.class);
        owner = createOwner();
    }

    @Test
    public void testCreateReadDelete() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        key.setServiceLevel("level1");
        key.setReleaseVer(new Release("release1"));
        activationKeyCurator.create(key);
        ActivationKeyController controller = new ActivationKeyController(activationKeyCurator, i18n,
            poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);

        assertNotNull(key.getId());
        ActivationKey output = controller.getActivationKey(key.getId());
        assertNotNull(output);

        controller.deleteActivationKey(key.getId());
        assertThrows(BadRequestException.class, () ->
            controller.getActivationKey(key.getId())
        );
    }

    @Test
    public void testInvalidTokenIdOnDelete() {
        ActivationKeyController akr = new ActivationKeyController(activationKeyCurator, i18n,
            poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);

        assertThrows(BadRequestException.class, () ->
            akr.deleteActivationKey("JarJarBinks")
        );
    }

    @Test
    public void testAddingRemovingPools() {
        ActivationKeyController akr = new ActivationKeyController(activationKeyCurator, i18n,
            poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);

        ActivationKey key = new ActivationKey();
        Product product = this.createProduct(owner);
        Pool pool = createPool(owner, product, 10L, new Date(), new Date());
        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);
        assertNotNull(key.getId());
        akr.addPoolToKey(key.getId(), pool.getId(), 1L);
        assertEquals(1, key.getPools().size());
        akr.removePoolFromKey(key.getId(), pool.getId());
        assertEquals(0, key.getPools().size());
    }

    @Test
    public void testReaddingPools() {
        ActivationKeyController akr = new ActivationKeyController(activationKeyCurator, i18n,
            poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);

        ActivationKey key = new ActivationKey();
        Product product = this.createProduct(owner);
        Pool pool = createPool(owner, product, 10L, new Date(), new Date());

        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());

        akr.addPoolToKey(key.getId(), pool.getId(), 1L);
        assertEquals(1, key.getPools().size());

        ActivationKey finalKey = key;
        assertThrows(BadRequestException.class, () ->
            akr.addPoolToKey(finalKey.getId(), pool.getId(), 1L)
        );
    }

    @Test
    public void testActivationKeyWithNonMultiPool() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "no");
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool"))).thenReturn(p);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
        assertThrows(BadRequestException.class, () ->
            akr.addPoolToKey("testKey", "testPool", 2L)
        );
    }

    @Test
    public void testActivationKeyWithNegPoolQuantity() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.setQuantity(10L);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool"))).thenReturn(p);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
        assertThrows(BadRequestException.class, () ->
            akr.addPoolToKey("testKey", "testPool", -3L)
        );
    }

    @Test
    public void testActivationKeyWithLargePoolQuantity() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        p.setQuantity(10L);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool"))).thenReturn(p);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
        akr.addPoolToKey("testKey", "testPool", 15L);
    }

    @Test
    public void testActivationKeyWithUnlimitedPool() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        p.setQuantity(-1L);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool"))).thenReturn(p);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
        akr.addPoolToKey("testKey", "testPool", 15L);
    }


    @Test
    public void testActivationKeyWithPersonConsumerType() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, "person");
        p.setQuantity(1L);
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool"))).thenReturn(p);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
        assertThrows(BadRequestException.class, () ->
            akr.addPoolToKey("testKey", "testPool", 1L)
        );
    }

    @Test
    public void testActivationKeyWithNonPersonConsumerType() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, "candlepin");
        PoolManager poolManager = mock(PoolManager.class);

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool"))).thenReturn(p);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
        assertNotNull(akr.addPoolToKey("testKey", "testPool", 1L));
    }

    @Test
    public void testActivationKeyWithSameHostReqPools() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = genPool();
        p1.setAttribute(Pool.Attributes.REQUIRES_HOST, "host1");
        Pool p2 = genPool();
        p2.setAttribute(Pool.Attributes.REQUIRES_HOST, "host1");

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool1"))).thenReturn(p1);
        when(poolManager.get(eq("testPool2"))).thenReturn(p2);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);

        akr.addPoolToKey("testKey", "testPool1", 1L);
        assertEquals(1, ak.getPools().size());
        Set<ActivationKeyPool> akPools = new HashSet<>();
        akPools.add(new ActivationKeyPool(ak, p1, 1L));
        ak.setPools(akPools);
        akr.addPoolToKey("testKey", "testPool2", 1L);
        assertEquals(2, ak.getPools().size());
    }

    @Test
    public void testActivationKeyWithDiffHostReqPools() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = genPool();
        p1.setAttribute(Pool.Attributes.REQUIRES_HOST, "host1");
        Pool p2 = genPool();
        p2.setAttribute(Pool.Attributes.REQUIRES_HOST, "host2");

        ak.addPool(p2, 1L);
        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool1"))).thenReturn(p1);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
        akr.addPoolToKey("testKey", "testPool1", 1L);
    }

    @Test
    public void testActivationKeyHostReqPoolThenNonHostReq() {
        ActivationKey ak = genActivationKey();
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        PoolManager poolManager = mock(PoolManager.class);
        Pool p1 = genPool();
        p1.setAttribute(Pool.Attributes.REQUIRES_HOST, "host1");
        Pool p2 = genPool();
        p2.setAttribute(Pool.Attributes.REQUIRES_HOST, "");

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool1"))).thenReturn(p1);
        when(poolManager.get(eq("testPool2"))).thenReturn(p2);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
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

        when(akc.secureGet(eq("testKey"))).thenReturn(ak);
        when(poolManager.get(eq("testPool"))).thenReturn(p);

        ActivationKeyController akr = new ActivationKeyController(
            akc, i18n, poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);
        akr.addPoolToKey("testKey", "testPool", null);
    }

    @Test
    public void testUpdateTooLongRelease() {
        ActivationKeyController akr = new ActivationKeyController(activationKeyCurator, i18n,
            poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);

        ActivationKey key = new ActivationKey();
        OwnerDTO ownerDto = new OwnerDTO();
        key.setOwner(owner);
        key.setName("dd");
        key.setServiceLevel("level1");
        key.setReleaseVer(new Release("release1"));
        activationKeyCurator.create(key);

        ActivationKeyDTO update = new ActivationKeyDTO();
        update.setOwner(ownerDto);
        update.setName("dd");
        update.setServiceLevel("level1");
        update.setReleaseVersion(TestUtil.getStringOfSize(256));

        assertThrows(BadRequestException.class, () ->
            akr.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testAddingRemovingProductIDs() {
        ActivationKeyController akr = new ActivationKeyController(activationKeyCurator, i18n,
            poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);

        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        Product product = this.createProduct(owner);

        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());
        akr.addProductIdToKey(key.getId(), product.getId());
        assertEquals(1, key.getProducts().size());
        akr.removeProductIdFromKey(key.getId(), product.getId());
        assertEquals(0, key.getProducts().size());
    }

    @Test
    public void testReaddingProductIDs() {
        ActivationKeyController akr = new ActivationKeyController(activationKeyCurator, i18n,
            poolManager, serviceLevelValidator, activationKeyRules, ownerProductCurator);

        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        Product product = this.createProduct(owner);

        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());

        akr.addProductIdToKey(key.getId(), product.getId());
        assertEquals(1, key.getProducts().size());

        ActivationKey finalKey = key;
        assertThrows(BadRequestException.class, () ->
            akr.addProductIdToKey(finalKey.getId(), product.getId())
        );
    }

    private Pool genPool() {
        Pool pool = new Pool();
        pool.setId(String.valueOf(poolid++));
        pool.setQuantity(10L);
        pool.setConsumed(4L);
        pool.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        pool.setProduct(TestUtil.createProduct());
        return pool;
    }

    private ActivationKey genActivationKey() {
        return new ActivationKey();
    }
}
