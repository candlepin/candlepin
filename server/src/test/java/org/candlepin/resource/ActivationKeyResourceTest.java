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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.ReleaseVerDTO;
import org.candlepin.model.Owner;
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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

/**
 * ActivationKeyResourceTest
 */
public class ActivationKeyResourceTest extends DatabaseTestFixture {

    private static int poolid = 0;

    @Inject private ServiceLevelValidator serviceLevelValidator;
    @Inject private Injector injector;

    private ActivationKeyResource activationKeyResource;
    private ActivationKeyRules activationKeyRules;

    private ActivationKeyCurator mockActivationKeyCurator;
    private PoolManager mockPoolManager;

    private Owner owner;

    @BeforeEach
    public void setUp() {
        activationKeyResource = injector.getInstance(ActivationKeyResource.class);
        activationKeyRules = injector.getInstance(ActivationKeyRules.class);

        this.mockActivationKeyCurator = mock(ActivationKeyCurator.class);
        this.mockPoolManager = mock(PoolManager.class);

        this.owner = createOwner();
    }

    private ActivationKeyResource buildActivationKeyResource() {
        return new ActivationKeyResource(this.mockActivationKeyCurator, this.i18n, this.mockPoolManager,
            this.serviceLevelValidator, this.activationKeyRules, null, this.modelTranslator, null);
    }


    @Test
    public void testCreateReadDelete() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        key.setServiceLevel("level1");
        key.setReleaseVer(new Release("release1"));
        activationKeyCurator.create(key);

        assertNotNull(key.getId());
        ActivationKeyDTO output = activationKeyResource.getActivationKey(key.getId());
        assertNotNull(output);

        activationKeyResource.deleteActivationKey(key.getId());
        assertThrows(BadRequestException.class, () ->
            activationKeyResource.getActivationKey(key.getId())
        );
    }

    @Test
    public void testInvalidTokenIdOnDelete() {
        assertThrows(BadRequestException.class, () ->
            activationKeyResource.deleteActivationKey("JarJarBinks")
        );
    }

    @Test
    public void testAddingRemovingPools() {
        ActivationKey key = new ActivationKey();
        Product product = this.createProduct(owner);
        Pool pool = createPool(owner, product, 10L, new Date(), new Date());
        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);
        assertNotNull(key.getId());
        activationKeyResource.addPoolToKey(key.getId(), pool.getId(), 1L);
        assertTrue(key.getPools().size() == 1);
        activationKeyResource.removePoolFromKey(key.getId(), pool.getId());
        assertTrue(key.getPools().size() == 0);
    }

    @Test
    public void testReaddingPools() {
        ActivationKey key = new ActivationKey();
        Product product = this.createProduct(owner);
        Pool pool = createPool(owner, product, 10L, new Date(), new Date());

        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());

        activationKeyResource.addPoolToKey(key.getId(), pool.getId(), 1L);
        assertTrue(key.getPools().size() == 1);

        ActivationKey finalKey = key;
        assertThrows(BadRequestException.class, () ->
            activationKeyResource.addPoolToKey(finalKey.getId(), pool.getId(), 1L)
        );
    }

    @Test
    public void testActivationKeyWithNonMultiPool() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "no");

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p).when(this.mockPoolManager).get(eq("testPool"));

        ActivationKeyResource akr = this.buildActivationKeyResource();
        assertThrows(BadRequestException.class, () -> akr.addPoolToKey("testKey", "testPool", 2L));
    }

    @Test
    public void testActivationKeyWithNegPoolQuantity() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.setQuantity(10L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p).when(this.mockPoolManager).get(eq("testPool"));

        ActivationKeyResource akr = this.buildActivationKeyResource();
        assertThrows(BadRequestException.class, () ->
            akr.addPoolToKey("testKey", "testPool", -3L)
        );
    }

    @Test
    public void testActivationKeyWithLargePoolQuantity() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        p.setQuantity(10L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p).when(this.mockPoolManager).get(eq("testPool"));

        ActivationKeyResource akr = this.buildActivationKeyResource();
        akr.addPoolToKey("testKey", "testPool", 15L);
    }

    @Test
    public void testActivationKeyWithUnlimitedPool() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        p.setQuantity(-1L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p).when(this.mockPoolManager).get(eq("testPool"));

        ActivationKeyResource akr = this.buildActivationKeyResource();
        akr.addPoolToKey("testKey", "testPool", 15L);
    }


    @Test
    public void testActivationKeyWithPersonConsumerType() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, "person");
        p.setQuantity(1L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p).when(this.mockPoolManager).get(eq("testPool"));

        ActivationKeyResource akr = this.buildActivationKeyResource();
        assertThrows(BadRequestException.class, () -> akr.addPoolToKey("testKey", "testPool", 1L));
    }

    @Test
    public void testActivationKeyWithNonPersonConsumerType() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, "candlepin");

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p).when(this.mockPoolManager).get(eq("testPool"));

        ActivationKeyResource akr = this.buildActivationKeyResource();
        assertNotNull(akr.addPoolToKey("testKey", "testPool", 1L));
    }

    @Test
    public void testActivationKeyWithSameHostReqPools() {
        ActivationKey ak = genActivationKey();
        Pool p1 = genPool();
        p1.setAttribute(Pool.Attributes.REQUIRES_HOST, "host1");
        Pool p2 = genPool();
        p2.setAttribute(Pool.Attributes.REQUIRES_HOST, "host1");

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p1).when(this.mockPoolManager).get(eq("testPool1"));
        doReturn(p2).when(this.mockPoolManager).get(eq("testPool2"));

        ActivationKeyResource akr = this.buildActivationKeyResource();

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
        Pool p1 = genPool();
        p1.setAttribute(Pool.Attributes.REQUIRES_HOST, "host1");
        Pool p2 = genPool();
        p2.setAttribute(Pool.Attributes.REQUIRES_HOST, "host2");
        ak.addPool(p2, 1L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p1).when(this.mockPoolManager).get(eq("testPool1"));

        ActivationKeyResource akr = this.buildActivationKeyResource();
        akr.addPoolToKey("testKey", "testPool1", 1L);
    }

    @Test
    public void testActivationKeyHostReqPoolThenNonHostReq() {
        ActivationKey ak = genActivationKey();
        Pool p1 = genPool();
        p1.setAttribute(Pool.Attributes.REQUIRES_HOST, "host1");
        Pool p2 = genPool();
        p2.setAttribute(Pool.Attributes.REQUIRES_HOST, "");

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p1).when(this.mockPoolManager).get(eq("testPool1"));
        doReturn(p2).when(this.mockPoolManager).get(eq("testPool2"));

        ActivationKeyResource akr = this.buildActivationKeyResource();

        akr.addPoolToKey("testKey", "testPool1", 1L);
        assertEquals(1, ak.getPools().size());
        ak.addPool(p1, 1L);
        akr.addPoolToKey("testKey", "testPool2", 1L);
        assertEquals(3, ak.getPools().size());
    }

    @Test
    public void testActivationKeyWithNullQuantity() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet(eq("testKey"));
        doReturn(p).when(this.mockPoolManager).get(eq("testPool"));

        ActivationKeyResource akr = this.buildActivationKeyResource();
        akr.addPoolToKey("testKey", "testPool", null);
    }

    @Test
    public void testUpdateTooLongRelease() {
        ActivationKey key = new ActivationKey();
        NestedOwnerDTO ownerDto = new NestedOwnerDTO();
        key.setOwner(owner);
        key.setName("dd");
        key.setServiceLevel("level1");
        key.setReleaseVer(new Release("release1"));
        activationKeyCurator.create(key);

        ActivationKeyDTO update = new ActivationKeyDTO();
        update.owner(ownerDto)
            .name("dd")
            .serviceLevel("level1")
            .releaseVer(new ReleaseVerDTO().releaseVer(TestUtil.getStringOfSize(256)));

        assertThrows(BadRequestException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testAddingRemovingProductIDs() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        Product product = this.createProduct(owner);

        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());
        activationKeyResource.addProductIdToKey(key.getId(), product.getId());
        assertTrue(key.getProducts().size() == 1);
        activationKeyResource.removeProductIdFromKey(key.getId(), product.getId());
        assertEquals(0, key.getProducts().size());
    }

    @Test
    public void testReaddingProductIDs() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        Product product = this.createProduct(owner);

        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());

        activationKeyResource.addProductIdToKey(key.getId(), product.getId());
        assertEquals(1, key.getProducts().size());

        ActivationKey finalKey = key;
        assertThrows(BadRequestException.class, () ->
            activationKeyResource.addProductIdToKey(finalKey.getId(), product.getId())
        );
    }

    @Test
    public void testValidationUpdateWithNullProductId() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ActivationKeyProductDTO> products = new HashSet<>();
        products.add(new ActivationKeyProductDTO().productId(null));
        update.products(products);

        assertThrows(ConstraintViolationException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithEmptyProductId() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ActivationKeyProductDTO> products = new HashSet<>();
        products.add(new ActivationKeyProductDTO().productId(""));
        update.products(products);

        assertThrows(ConstraintViolationException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithNullProduct() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ActivationKeyProductDTO> products = new HashSet<>();
        products.add(null);
        update.products(products);

        assertThrows(IllegalArgumentException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithNullPoolId() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(new ActivationKeyPoolDTO().poolId(null));
        update.pools(pools);

        assertThrows(ConstraintViolationException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithEmptyPoolId() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(new ActivationKeyPoolDTO().poolId(""));
        update.pools(pools);

        assertThrows(ConstraintViolationException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithNullPool() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(null);
        update.pools(pools);

        assertThrows(IllegalArgumentException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithNullContentOverrideName() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(new ContentOverrideDTO().name(null).contentLabel("a label"));
        update.contentOverrides(contentOverrideDTOS);

        assertThrows(ConstraintViolationException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithEmptyContentOverrideName() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(new ContentOverrideDTO().name("").contentLabel("a label"));
        update.contentOverrides(contentOverrideDTOS);

        assertThrows(ConstraintViolationException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithNullContentOverrideLabel() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(new ContentOverrideDTO().name("a name").contentLabel(null));
        update.contentOverrides(contentOverrideDTOS);

        assertThrows(ConstraintViolationException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithEmptyContentOverrideLabel() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(new ContentOverrideDTO().name("a name").contentLabel(""));
        update.contentOverrides(contentOverrideDTOS);

        assertThrows(ConstraintViolationException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
        );
    }

    @Test
    public void testValidationUpdateWithNullContentOverride() {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("dd");
        activationKeyCurator.create(key);

        assertNotNull(key.getId());

        ActivationKeyDTO update = new ActivationKeyDTO();
        Set<ContentOverrideDTO> contentOverrideDTOS = new HashSet<>();
        contentOverrideDTOS.add(null);
        update.contentOverrides(contentOverrideDTOS);

        assertThrows(IllegalArgumentException.class, () ->
            activationKeyResource.updateActivationKey(key.getId(), update)
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
