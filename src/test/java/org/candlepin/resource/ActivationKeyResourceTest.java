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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.controller.ContentAccessMode;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.api.server.v1.ActivationKeyDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.ReleaseVerDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.AbstractHibernateCurator;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;



public class ActivationKeyResourceTest extends DatabaseTestFixture {

    private static int poolid = 0;

    private DTOValidator dtoValidator;
    private ServiceLevelValidator mockServiceLevelValidator;
    private ActivationKeyResource activationKeyResource;
    private ActivationKeyRules activationKeyRules;

    private ActivationKeyCurator mockActivationKeyCurator;
    private PoolService mockPoolService;
    private ActivationKeyContentOverrideCurator mockAkcoCurator;
    private ContentOverrideValidator mockCoValidator;
    private Owner owner;

    @BeforeEach
    public void setUp() {
        activationKeyResource = injector.getInstance(ActivationKeyResource.class);
        activationKeyRules = injector.getInstance(ActivationKeyRules.class);

        this.dtoValidator = new DTOValidator(this.i18n);
        this.mockActivationKeyCurator = mock(ActivationKeyCurator.class);
        this.mockServiceLevelValidator = mock(ServiceLevelValidator.class);
        this.mockPoolService = mock(PoolService.class);
        this.mockAkcoCurator = mock(ActivationKeyContentOverrideCurator.class);
        this.mockCoValidator = mock(ContentOverrideValidator.class);

        this.owner = createOwner();
    }

    private ActivationKeyResource buildActivationKeyResource() {
        return new ActivationKeyResource(this.mockActivationKeyCurator, this.i18n, this.mockPoolService,
            this.mockServiceLevelValidator, this.activationKeyRules, this.productCurator,
            this.modelTranslator, this.dtoValidator, this.mockAkcoCurator, this.mockCoValidator);
    }

    private void mockCuratorPassthroughMethods(AbstractHibernateCurator mock) {
        doAnswer(returnsFirstArg()).when(mock).merge(any(AbstractHibernateObject.class));

        // Add more mocks here as necessary (or update the tests to not use mocks)
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
        assertThrows(BadRequestException.class, () -> activationKeyResource.getActivationKey(key.getId()));
    }

    @Test
    public void testInvalidTokenIdOnDelete() {
        assertThrows(BadRequestException.class,
            () -> activationKeyResource.deleteActivationKey("JarJarBinks"));
    }

    @Test
    public void testAddingRemovingPools() {
        Owner nonSCAOwner = createNonSCAOwner("test_owner");
        ActivationKey key = new ActivationKey();
        Product product = this.createProduct();
        Pool pool = createPool(nonSCAOwner, product, 10L, new Date(), new Date());
        key.setOwner(nonSCAOwner);
        key.setName("dd");
        key = activationKeyCurator.create(key);
        assertNotNull(key.getId());
        activationKeyResource.addPoolToKey(key.getId(), pool.getId(), 1L);
        assertEquals(1, key.getPools().size());
        activationKeyResource.removePoolFromKey(key.getId(), pool.getId());
        assertEquals(0, key.getPools().size());
    }

    @Test
    public void testReadingPools() {
        Owner nonSCAOwner = createNonSCAOwner("test_owner");
        ActivationKey key = new ActivationKey();
        Product product = this.createProduct();
        Pool pool = createPool(nonSCAOwner, product, 10L, new Date(), new Date());

        key.setOwner(nonSCAOwner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());

        activationKeyResource.addPoolToKey(key.getId(), pool.getId(), 1L);
        assertEquals(1, key.getPools().size());

        ActivationKey finalKey = key;
        assertThrows(BadRequestException.class,
            () -> activationKeyResource.addPoolToKey(finalKey.getId(), pool.getId(), 1L));
    }

    @Test
    public void testActivationKeyWithNonMultiPool() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "no");

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p).when(this.mockPoolService).get("testPool");

        ActivationKeyResource akr = this.buildActivationKeyResource();
        assertThrows(BadRequestException.class, () -> akr.addPoolToKey("testKey", "testPool", 2L));
    }

    @Test
    public void testActivationKeyWithNegPoolQuantity() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.setQuantity(10L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p).when(this.mockPoolService).get("testPool");

        ActivationKeyResource akr = this.buildActivationKeyResource();
        assertThrows(BadRequestException.class, () -> akr.addPoolToKey("testKey", "testPool", -3L));
    }

    @Test
    public void testActivationKeyWithLargePoolQuantity() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        p.setQuantity(10L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p).when(this.mockPoolService).get("testPool");

        ActivationKeyResource akr = this.buildActivationKeyResource();
        akr.addPoolToKey("testKey", "testPool", 15L);
    }

    @Test
    public void testActivationKeyWithUnlimitedPool() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        p.setQuantity(-1L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p).when(this.mockPoolService).get("testPool");

        ActivationKeyResource akr = this.buildActivationKeyResource();
        akr.addPoolToKey("testKey", "testPool", 15L);
    }

    @Test
    public void testActivationKeyWithPersonConsumerType() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, "person");
        p.setQuantity(1L);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p).when(this.mockPoolService).get("testPool");

        ActivationKeyResource akr = this.buildActivationKeyResource();
        assertThrows(BadRequestException.class, () -> akr.addPoolToKey("testKey", "testPool", 1L));
    }

    @Test
    public void testActivationKeyWithNonPersonConsumerType() {
        ActivationKey ak = genActivationKey();
        Pool p = genPool();
        p.getProduct().setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, "candlepin");

        this.mockCuratorPassthroughMethods(this.mockActivationKeyCurator);

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p).when(this.mockPoolService).get("testPool");

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

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p1).when(this.mockPoolService).get("testPool1");
        doReturn(p2).when(this.mockPoolService).get("testPool2");

        ActivationKeyResource akr = this.buildActivationKeyResource();

        akr.addPoolToKey("testKey", "testPool1", 1L);
        assertEquals(1, ak.getPools().size());

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

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p1).when(this.mockPoolService).get("testPool1");

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

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p1).when(this.mockPoolService).get("testPool1");
        doReturn(p2).when(this.mockPoolService).get("testPool2");

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

        doReturn(ak).when(this.mockActivationKeyCurator).secureGet("testKey");
        doReturn(p).when(this.mockPoolService).get("testPool");

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

        assertThrows(BadRequestException.class,
            () -> activationKeyResource.updateActivationKey(key.getId(), update));
    }

    @Test
    public void testAddingRemovingProductIDs() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        Product product = this.createProduct();

        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());
        activationKeyResource.addProductIdToKey(key.getId(), product.getId());
        assertEquals(1, key.getProductIds().size());
        activationKeyResource.removeProductIdFromKey(key.getId(), product.getId());
        assertEquals(0, key.getProductIds().size());
    }

    @Test
    public void testReaddingProductIDs() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        Product product = this.createProduct();

        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyCurator.create(key);

        assertNotNull(key.getId());

        activationKeyResource.addProductIdToKey(key.getId(), product.getId());
        assertEquals(1, key.getProductIds().size());

        ActivationKey finalKey = key;
        assertThrows(BadRequestException.class,
            () -> activationKeyResource.addProductIdToKey(finalKey.getId(), product.getId()));
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

        assertThrows(BadRequestException.class,
            () -> activationKeyResource.updateActivationKey(key.getId(), update));
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

        assertThrows(BadRequestException.class,
            () -> activationKeyResource.updateActivationKey(key.getId(), update));
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

        assertThrows(BadRequestException.class,
            () -> activationKeyResource.updateActivationKey(key.getId(), update));
    }

    @Test
    public void testNotNullFieldsUpdate() {
        ActivationKey toUpdate = spy(createActivationKey(owner));
        when(this.mockActivationKeyCurator.secureGet(toUpdate.getId())).thenReturn(toUpdate);
        String ownerId = owner.getId();
        doNothing().when(mockServiceLevelValidator).validate(eq(ownerId), any(String.class));

        ActivationKeyDTO update = new ActivationKeyDTO()
            .id(toUpdate.getId())
            .name("KeyName")
            .serviceLevel("Key Level")
            .releaseVer(new ReleaseVerDTO().releaseVer("Key Release"))
            .description("Key Decription")
            .usage("Key Usage")
            .role("Key Role")
            .addOns(Set.of("Key Addon"))
            .autoAttach(true);
        buildActivationKeyResource().updateActivationKey(toUpdate.getId(), update);

        verify(toUpdate, times(1)).setName(update.getName());
        verify(toUpdate, times(1)).setServiceLevel(update.getServiceLevel());
        verify(toUpdate, times(1)).setReleaseVer(any(Release.class));
        verify(toUpdate, times(1)).setDescription(update.getDescription());
        verify(toUpdate, times(1)).setUsage(update.getUsage());
        verify(toUpdate, times(1)).setAddOns(update.getAddOns());
        verify(toUpdate, times(1)).setAutoAttach(update.getAutoAttach());
    }

    @Test
    public void testAddActivationKeyContentOverrides() {
        Owner owner = this.createOwner(TestUtil.randomString());
        ActivationKey key1 = this.createActivationKey(owner);
        ActivationKey key2 = this.createActivationKey(owner);

        List<ContentOverrideDTO> overridesToAdd = new ArrayList<>();

        for (int idx = 1; idx <= 3; ++idx) {
            String name = String.format("existing-key1-co-%d", idx);
            String label = String.format("existing-key1-label-%d", idx);

            // Create and persist some initial key 1 content overrides
            ActivationKeyContentOverride contentOverride = new ActivationKeyContentOverride()
                .setKey(key1)
                .setName(name)
                .setContentLabel(label)
                .setValue(TestUtil.randomString());

            contentOverride = this.activationKeyContentOverrideCurator.create(contentOverride);

            // Create and persist some initial key 2 content overrides
            ActivationKeyContentOverride key2ContentOverride = new ActivationKeyContentOverride()
                .setKey(key2)
                .setName(String.format("existing-key2-co-%d", idx))
                .setContentLabel(String.format("existing-key2-label-%d", idx))
                .setValue(TestUtil.randomString());

            key2ContentOverride = this.activationKeyContentOverrideCurator.create(key2ContentOverride);

            // Add a content override to update the persisted ActivationKeyContentOverride
            ContentOverrideDTO contentOverrideUpdate = new ContentOverrideDTO();
            contentOverrideUpdate.setName(name);
            contentOverrideUpdate.setContentLabel(label);
            contentOverrideUpdate.setValue(TestUtil.randomString(label + "-modified-"));

            overridesToAdd.add(contentOverrideUpdate);

            // Add an unpersisted net new content overrides
            ContentOverrideDTO netNewContentOverride = new ContentOverrideDTO();
            netNewContentOverride.setName(String.format("new-co-%d", idx));
            netNewContentOverride.setContentLabel(String.format("new-label-%d", idx));
            netNewContentOverride.setValue(TestUtil.randomString());

            overridesToAdd.add(netNewContentOverride);
        }

        Stream<ContentOverrideDTO> actual = this.activationKeyResource
            .addActivationKeyContentOverrides(key1.getId(), overridesToAdd);

        assertThat(actual)
            .isNotNull()
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(overridesToAdd);
    }

    @Test
    public void testAddPoolToKeyFailsInSCAMode() {
        Owner owner = new Owner()
            .setId("test-id")
            .setKey("test-org")
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        ActivationKey key = new ActivationKey("test-key", owner);
        key.setId("test-key");

        Pool pool = new Pool();
        pool.setId("test-pool");

        doReturn(pool).when(this.mockPoolService).get(pool.getId());
        doReturn(key).when(this.mockActivationKeyCurator).secureGet(key.getId());

        ActivationKeyResource resource = this.buildActivationKeyResource();

        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> resource.addPoolToKey(key.getId(), pool.getId(), 1L));

        assertTrue(exception.getMessage().contains("simple content access"));
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
