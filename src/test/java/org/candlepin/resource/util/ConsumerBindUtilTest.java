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
package org.candlepin.resource.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.controller.Entitler;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ServiceLevelValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;



@ExtendWith(MockitoExtension.class)
public class ConsumerBindUtilTest {

    @Mock
    private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private Entitler entitler;
    @Mock
    private ServiceLevelValidator serviceLevelValidator;
    @Mock
    private PoolCurator poolCurator;

    private I18n i18n;

    private ConsumerType systemConsumerType;
    private Owner owner;

    @BeforeEach
    public void init() throws Exception {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        this.systemConsumerType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        this.systemConsumerType.setId("test-ctype-" + TestUtil.randomInt());
        this.owner = TestUtil.createOwner();
        this.owner.setId(TestUtil.randomString());
        this.owner.setContentAccessMode("entitlement");
    }

    private ConsumerBindUtil buildConsumerBindUtil() {
        return new ConsumerBindUtil(this.entitler, this.i18n, this.consumerContentOverrideCurator,
            this.ownerCurator, null, this.serviceLevelValidator, this.poolCurator);
    }

    private List<ActivationKey> mockActivationKeys() {
        ActivationKey key1 = new ActivationKey("key1", this.owner);
        ActivationKey key2 = new ActivationKey("key2", this.owner);
        ActivationKey key3 = new ActivationKey("key3", this.owner);
        List<ActivationKey> keys = new LinkedList<>();
        keys.add(key1);
        keys.add(key2);
        keys.add(key3);
        return keys;
    }

    private Pool createTestPool(Owner owner, int quantity) {
        Product product = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(owner, product, quantity);
        pool.setId("test_pool-" + TestUtil.randomInt());

        return pool;
    }

    @Test
    public void registerWithKeyWithPoolAndInstalledProductsAutoAttach() throws Exception {
        Product prod = TestUtil.createProduct();
        Set<String> prodIds = Set.of(prod.getId());

        Pool pool = TestUtil.createPool(owner, prod)
            .setId("id-string");

        List<String> poolIds = List.of(pool.getId());

        ActivationKey key1 = new ActivationKey("key1", owner)
            .addPool(pool, 0L)
            .setAutoAttach(true);

        List<ActivationKey> keys = List.of(key1);

        ConsumerInstalledProduct cip = new ConsumerInstalledProduct()
            .setProductId(prod.getId())
            .setProductName(prod.getName());

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType)
            .setInstalledProducts(Set.of(cip));

        AutobindData ad = new AutobindData(consumer, owner)
            .withPools(poolIds)
            .forProducts(prodIds);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
        verify(entitler).bindByProducts(eq(ad));
    }

    @Test
    public void registerWithKeyWithInstalledProductsAutoAttach() throws Exception {
        Product prod = TestUtil.createProduct();
        Set<String> prodIds = Set.of(prod.getId());

        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.setAutoAttach(true);

        ConsumerInstalledProduct cip = new ConsumerInstalledProduct()
            .setProductId(prod.getId())
            .setProductName(prod.getName());

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType)
            .setInstalledProducts(Set.of(cip));

        AutobindData ad = new AutobindData(consumer, owner)
            .forProducts(prodIds);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
        verify(entitler).bindByProducts(eq(ad));
    }

    @Test
    public void registerWithKeyWithInstalledProductsPlusAutoAttach() throws Exception {
        // installed product
        Product prod1 = TestUtil.createProduct();
        // key product
        Product prod2 = TestUtil.createProduct();
        Set<String> prodIds = Set.of(prod1.getId(), prod2.getId());

        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.addProduct(prod2);
        key1.setAutoAttach(true);

        ConsumerInstalledProduct cip = new ConsumerInstalledProduct()
            .setProductId(prod1.getId())
            .setProductName(prod1.getName());

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType)
            .setInstalledProducts(Set.of(cip));

        AutobindData ad = new AutobindData(consumer, owner)
            .forProducts(prodIds);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
        verify(entitler).bindByProducts(eq(ad));
    }

    @Test
    public void registerFailWithKeyServiceLevelNotExist() throws Exception {
        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.setServiceLevel("I don't exist");

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        doThrow(new BadRequestException("exception")).when(serviceLevelValidator)
            .validate(eq(owner.getId()), eq(key1.getServiceLevel()));

        assertThrows(BadRequestException.class,
            () -> consumerBindUtil.handleActivationKeys(consumer, keys, false));
    }

    @Test
    public void registerPassWithKeyServiceLevelNotExistOtherKeysSucceed() throws Exception {
        List<ActivationKey> keys = mockActivationKeys();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.setServiceLevel("I don't exist");

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        doThrow(new BadRequestException("exception")).when(serviceLevelValidator)
            .validate(eq(owner.getId()), eq(key1.getServiceLevel()));
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
    }

    @Test
    public void registerFailWithNoGoodKeyPool() throws Exception {
        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);

        Product prod1 = TestUtil.createProduct();
        Pool ghost = TestUtil.createPool(owner, prod1, 5);
        ghost.setId("ghost-pool");
        key1.addPool(ghost, 10L);

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        when(entitler.bindByPoolQuantity(eq(consumer), eq(ghost.getId()), eq(10)))
            .thenThrow(new ForbiddenException("fail"));

        assertThrows(BadRequestException.class,
            () -> consumerBindUtil.handleActivationKeys(consumer, keys, false));
    }

    @Test
    public void registerPassWithOneGoodKeyPool() throws Exception {
        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);

        Product prod1 = TestUtil.createProduct();
        Pool pool1 = TestUtil.createPool(owner, prod1, 5);
        pool1.setId("pool1");
        key1.addPool(pool1, 10L);
        Product prod2 = TestUtil.createProduct();
        Pool pool2 = TestUtil.createPool(owner, prod2, 5);
        pool2.setId("pool2");
        key1.addPool(pool2, 10L);
        Product prod3 = TestUtil.createProduct();
        Pool pool3 = TestUtil.createPool(owner, prod3, 5);
        pool3.setId("pool3");
        key1.addPool(pool3, 5L);

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        when(entitler.bindByPoolQuantity(eq(consumer), eq(pool1.getId()), eq(10)))
            .thenThrow(new ForbiddenException("fail"));
        when(entitler.bindByPoolQuantity(eq(consumer), eq(pool2.getId()), eq(10)))
            .thenThrow(new ForbiddenException("fail"));

        consumerBindUtil.handleActivationKeys(consumer, keys, false);
    }

    @Test
    public void registerPassWithOneGoodKey() throws Exception {
        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        ActivationKey key2 = new ActivationKey("key2", owner);
        keys.add(key1);
        keys.add(key2);

        Product prod1 = TestUtil.createProduct();
        Pool pool1 = TestUtil.createPool(owner, prod1, 5);
        pool1.setId("pool1");
        key1.addPool(pool1, 10L);
        Product prod2 = TestUtil.createProduct();
        Pool pool2 = TestUtil.createPool(owner, prod2, 5);
        pool2.setId("pool2");
        key1.addPool(pool2, 10L);
        Product prod3 = TestUtil.createProduct();
        Pool pool3 = TestUtil.createPool(owner, prod3, 5);
        pool3.setId("pool3");
        key2.addPool(pool3, 5L);

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        when(entitler.bindByPoolQuantity(eq(consumer), eq(pool1.getId()), eq(10)))
            .thenThrow(new ForbiddenException("fail"));
        when(entitler.bindByPoolQuantity(eq(consumer), eq(pool2.getId()), eq(10)))
            .thenThrow(new ForbiddenException("fail"));

        consumerBindUtil.handleActivationKeys(consumer, keys, false);
    }

    @Test
    public void registerPassWhenAutobindDisabledForOwnerAndKeyHasAutobindEnabled() throws Exception {
        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        key1.setAutoAttach(true);
        keys.add(key1);

        Product prod1 = TestUtil.createProduct();
        Pool pool1 = TestUtil.createPool(owner, prod1, 5);
        pool1.setId("pool1");
        key1.addPool(pool1, 10L);

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        consumerBindUtil.handleActivationKeys(consumer, keys, true);
    }

    @Test
    public void handleActivationKeysWithoutAutoAttachUsingSCA() throws Exception {
        this.owner.setContentAccessModeList(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        this.owner.setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        Pool pool1 = this.createTestPool(this.owner, 1);

        ActivationKey key1 = new ActivationKey("test_key-1", this.owner);
        key1.setAutoAttach(false);
        key1.addPool(pool1, 1L);

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        consumerBindUtil.handleActivationKeys(consumer, Arrays.asList(key1), false);

        verify(this.entitler, times(1)).bindByPoolQuantity(eq(consumer), eq(pool1.getId()), eq(1));
    }

    @Test
    public void handleActivationKeysWithoutAutoAttachUsingSCAWhenBindFails() throws Exception {
        this.owner.setContentAccessModeList(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        this.owner.setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        Pool pool1 = this.createTestPool(this.owner, 1);

        ActivationKey key1 = new ActivationKey("test_key-1", this.owner);
        key1.setAutoAttach(false);
        key1.addPool(pool1, 5L);

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        doThrow(new ForbiddenException("exception")).when(this.entitler)
            .bindByPoolQuantity(eq(consumer), eq(pool1.getId()), eq(5));

        // This should not throw an exception even though the bind fails
        consumerBindUtil.handleActivationKeys(consumer, Arrays.asList(key1), false);

        verify(this.entitler, times(1)).bindByPoolQuantity(eq(consumer), eq(pool1.getId()), eq(5));
    }

    @Test
    public void handleActivationKeysWithAutoAttachUsingSCA() throws Exception {
        this.owner.setContentAccessModeList(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        this.owner.setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        ActivationKey key1 = new ActivationKey("test_key-1", this.owner);
        key1.setAutoAttach(true);

        Consumer consumer = new Consumer()
            .setName("sys.example.com")
            .setType(this.systemConsumerType);

        ConsumerBindUtil consumerBindUtil = this.buildConsumerBindUtil();

        consumerBindUtil.handleActivationKeys(consumer, Arrays.asList(key1), false);

        // Bind should not be invoked if we're in SCA mode
        verify(this.entitler, never()).bindByProducts(any(AutobindData.class));
    }
}
