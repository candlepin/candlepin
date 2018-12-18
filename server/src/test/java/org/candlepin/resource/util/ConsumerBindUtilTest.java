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
package org.candlepin.resource.util;

import static org.mockito.Mockito.*;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Role;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ServiceLevelValidator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
/*
 *
 */
public class ConsumerBindUtilTest {

    private static final String USER = "testuser";

    @Mock private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private Entitler entitler;
    @Mock private ServiceLevelValidator serviceLevelValidator;

    private I18n i18n;

    private ConsumerType system;
    protected Owner owner;
    protected Role role;

    private ConsumerBindUtil consumerBindUtil;

    @Before
    public void init() throws Exception {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        this.system = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        this.system.setId("test-ctype-" + TestUtil.randomInt());
        owner = TestUtil.createOwner();
        owner.setId(TestUtil.randomString());
        consumerBindUtil = new ConsumerBindUtil(
            this.entitler,
            this.i18n,
            this.consumerContentOverrideCurator,
            this.ownerCurator,
            null,
            this.serviceLevelValidator
        );
    }

    private List<ActivationKey> mockActivationKeys() {
        ActivationKey key1 = new ActivationKey("key1", owner);
        ActivationKey key2 = new ActivationKey("key2", owner);
        ActivationKey key3 = new ActivationKey("key3", owner);
        List<ActivationKey> keys = new LinkedList<>();
        keys.add(key1);
        keys.add(key2);
        keys.add(key3);
        return keys;
    }

    @Test
    public void registerWithKeyWithPoolAndInstalledProductsAutoAttach() throws Exception {
        Product prod = TestUtil.createProduct();
        String[] prodIds = new String[]{prod.getId()};

        Pool pool = TestUtil.createPool(owner, prod);
        pool.setId("id-string");
        List<String> poolIds = new ArrayList<>();
        poolIds.add(pool.getId());

        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.addPool(pool, 0L);
        key1.setAutoAttach(true);

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        Set<ConsumerInstalledProduct> cips = new HashSet<>();
        ConsumerInstalledProduct cip = new ConsumerInstalledProduct(consumer, prod);
        cips.add(cip);
        consumer.setInstalledProducts(cips);

        AutobindData ad = new AutobindData(consumer, owner).withPools(poolIds).forProducts(prodIds);
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
        verify(entitler).bindByProducts(eq(ad));
    }

    @Test
    public void registerWithKeyWithInstalledProductsAutoAttach() throws Exception {
        Product prod = TestUtil.createProduct();
        String[] prodIds = new String[]{prod.getId()};

        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.setAutoAttach(true);

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        Set<ConsumerInstalledProduct> cips = new HashSet<>();
        ConsumerInstalledProduct cip = new ConsumerInstalledProduct(consumer, prod);
        cips.add(cip);
        consumer.setInstalledProducts(cips);

        AutobindData ad = new AutobindData(consumer, owner).forProducts(prodIds);
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
        verify(entitler).bindByProducts(eq(ad));
    }

    @Test
    public void registerWithKeyWithInstalledProductsPlusAutoAttach() throws Exception {
        // installed product
        Product prod1 = TestUtil.createProduct();
        // key product
        Product prod2 = TestUtil.createProduct();
        String[] prodIds = new String[]{prod1.getId(), prod2.getId()};

        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.addProduct(prod2);
        key1.setAutoAttach(true);

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        Set<ConsumerInstalledProduct> cips = new HashSet<>();
        ConsumerInstalledProduct cip = new ConsumerInstalledProduct(consumer, prod1);
        cips.add(cip);
        consumer.setInstalledProducts(cips);

        AutobindData ad = new AutobindData(consumer, owner).forProducts(prodIds);
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
        verify(entitler).bindByProducts(eq(ad));
    }

    @Test(expected = BadRequestException.class)
    public void registerFailWithKeyServiceLevelNotExist() throws Exception {
        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.setServiceLevel("I don't exist");

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        doThrow(new BadRequestException("exception")).when(serviceLevelValidator)
            .validate(eq(owner.getId()), eq(key1.getServiceLevel()));
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
    }

    @Test
    public void registerPassWithKeyServiceLevelNotExistOtherKeysSucceed() throws Exception {
        List<ActivationKey> keys = mockActivationKeys();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);
        key1.setServiceLevel("I don't exist");

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        doThrow(new BadRequestException("exception")).when(serviceLevelValidator)
            .validate(eq(owner.getId()), eq(key1.getServiceLevel()));
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
    }

    @Test(expected = BadRequestException.class)
    public void registerFailWithNoGoodKeyPool() throws Exception {
        List<ActivationKey> keys = new ArrayList<>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        keys.add(key1);

        Product prod1 = TestUtil.createProduct();
        Pool ghost = TestUtil.createPool(owner, prod1, 5);
        ghost.setId("ghost-pool");
        key1.addPool(ghost, 10L);

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        when(entitler.bindByPoolQuantity(eq(consumer), eq(ghost.getId()), eq(10)))
            .thenThrow(new ForbiddenException("fail"));
        consumerBindUtil.handleActivationKeys(consumer, keys, false);
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

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
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

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
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

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        when(entitler.bindByPoolQuantity(eq(consumer), eq(pool1.getId()), eq(10)))
                .thenThrow(new ForbiddenException("fail"));
        consumerBindUtil.handleActivationKeys(consumer, keys, true);
    }

}
