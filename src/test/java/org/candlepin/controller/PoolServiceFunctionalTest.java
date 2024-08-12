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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.audit.EventSink;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQualifier;
import org.candlepin.model.Product;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



public class PoolServiceFunctionalTest extends DatabaseTestFixture {
    private static final String PRODUCT_MONITORING = "monitoring";
    private static final String PRODUCT_PROVISIONING = "provisioning";
    private static final String PRODUCT_VIRT_HOST = "virtualization_host";
    private static final String PRODUCT_VIRT_HOST_PLATFORM = "virtualization_host_platform";
    private static final String PRODUCT_VIRT_GUEST = "virt_guest";

    private PoolManager poolManager;
    private PoolService poolService;

    private Product monitoring;
    private Product provisioning;
    private Product socketLimitedProduct;

    private SubscriptionDTO sub4;

    private ConsumerType systemType;

    private Owner o;
    private Consumer parentSystem;
    private Consumer childVirtSystem;
    private EventSink eventSink;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();

        poolManager = injector.getInstance(PoolManager.class);
        poolService = injector.getInstance(PoolService.class);
        RefresherFactory refresherFactory = injector.getInstance(RefresherFactory.class);

        o = createOwner();
        ownerCurator.create(o);

        Product virtHost = TestUtil.createProduct(PRODUCT_VIRT_HOST, PRODUCT_VIRT_HOST);
        Product virtHostPlatform = TestUtil.createProduct(PRODUCT_VIRT_HOST_PLATFORM,
            PRODUCT_VIRT_HOST_PLATFORM);
        Product virtGuest = TestUtil.createProduct(PRODUCT_VIRT_GUEST, PRODUCT_VIRT_GUEST);
        monitoring = TestUtil.createProduct(PRODUCT_MONITORING, PRODUCT_MONITORING);
        monitoring.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");

        provisioning = TestUtil.createProduct(PRODUCT_PROVISIONING, PRODUCT_PROVISIONING);
        provisioning.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        provisioning.setMultiplier(2L);
        provisioning.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "4");

        virtHost.setAttribute(PRODUCT_VIRT_HOST, "");
        virtHostPlatform.setAttribute(PRODUCT_VIRT_HOST_PLATFORM, "");
        virtGuest.setAttribute(PRODUCT_VIRT_GUEST, "");
        monitoring.setAttribute(PRODUCT_MONITORING, "");
        provisioning.setAttribute(PRODUCT_PROVISIONING, "");

        socketLimitedProduct = TestUtil.createProduct("socket-limited-prod", "Socket Limited Product");
        socketLimitedProduct.setAttribute(Product.Attributes.SOCKETS, "2");
        productCurator.create(socketLimitedProduct);

        productCurator.create(virtHost);
        productCurator.create(virtHostPlatform);
        productCurator.create(virtGuest);
        productCurator.create(monitoring);
        productCurator.create(provisioning);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();

        SubscriptionDTO sub1 = new SubscriptionDTO();
        sub1.setId(Util.generateDbUUID());
        sub1.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub1.setProduct(this.modelTranslator.translate(virtHost, ProductDTO.class));
        sub1.setQuantity(5L);
        sub1.setStartDate(new Date());
        sub1.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub1.setLastModified(new Date());

        SubscriptionDTO sub2 = new SubscriptionDTO();
        sub2.setId(Util.generateDbUUID());
        sub2.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub2.setProduct(this.modelTranslator.translate(virtHostPlatform, ProductDTO.class));
        sub2.setQuantity(5L);
        sub2.setStartDate(new Date());
        sub2.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub2.setLastModified(new Date());

        SubscriptionDTO sub3 = new SubscriptionDTO();
        sub3.setId(Util.generateDbUUID());
        sub3.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub3.setProduct(this.modelTranslator.translate(monitoring, ProductDTO.class));
        sub3.setQuantity(5L);
        sub3.setStartDate(new Date());
        sub3.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub3.setLastModified(new Date());

        BrandingDTO brand1 = new BrandingDTO();
        brand1.setName("branding1");
        brand1.setType("type1");
        brand1.setProductId("product1");

        BrandingDTO brand2 = new BrandingDTO();
        brand2.setName("branding2");
        brand2.setType("type2");
        brand2.setProductId("product2");

        sub4 = new SubscriptionDTO();
        sub4.setId(Util.generateDbUUID());
        sub4.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub4.setProduct(this.modelTranslator.translate(provisioning, ProductDTO.class));
        sub4.getProduct().addBranding(brand1);
        sub4.getProduct().addBranding(brand2);

        sub4.setQuantity(5L);
        sub4.setStartDate(new Date());
        sub4.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub4.setLastModified(new Date());

        subscriptions.add(sub1);
        subscriptions.add(sub2);
        subscriptions.add(sub3);
        subscriptions.add(sub4);

        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);

        refresherFactory.getRefresher(subAdapter).add(o).run();

        this.systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(systemType);

        parentSystem = new Consumer()
            .setName("system")
            .setUsername("user")
            .setOwner(o)
            .setType(systemType)
            .setFact("total_guests", "0");
        consumerCurator.create(parentSystem);

        childVirtSystem = new Consumer()
            .setName("virt system")
            .setUsername("user")
            .setOwner(o)
            .setType(systemType);

        consumerCurator.create(childVirtSystem);
    }

    @Test
    public void testDeletePool() {
        Pool pool = createPool(o, socketLimitedProduct, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));

        List<Pool> pools = poolCurator.listByOwner(o);
        assertEquals(5, poolCurator.listByOwner(o).size());

        this.poolService.deletePools(Arrays.asList(pool, pools.get(0)));

        pools = poolCurator.listByOwner(o);
        assertEquals(3, pools.size());

        this.poolService.deletePools(pools);

        pools = poolCurator.listByOwner(o);
        assertTrue(pools.isEmpty());
    }

    @Test
    public void testRevocation() throws Exception {
        AutobindData data = new AutobindData(parentSystem, o)
            .on(new Date())
            .forProducts(Set.of(monitoring.getId()));

        Entitlement e = poolManager.entitleByProducts(data).get(0);
        this.poolService.revokeEntitlement(e);

        List<Entitlement> entitlements = entitlementCurator.listByConsumer(parentSystem);
        assertTrue(entitlements.isEmpty());
    }

    @Test
    public void testConsumeQuantity() throws Exception {
        PoolQualifier qualifier = new PoolQualifier()
            .setOwnerId(o.getId())
            .addProductId(monitoring.getId());

        Pool monitoringPool = poolCurator.listAvailableEntitlementPools(qualifier)
            .getPageData()
            .get(0);
        assertEquals(Long.valueOf(5), monitoringPool.getQuantity());

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(monitoringPool.getId(), 3);
        List<Entitlement> eList = poolManager.entitleByPools(parentSystem, poolQuantities);
        assertEquals(1, eList.size());
        assertEquals(Long.valueOf(3), monitoringPool.getConsumed());
        consumerCurator.get(parentSystem.getId());
        assertEquals(3, parentSystem.getEntitlementCount());

        this.poolService.revokeEntitlement(eList.get(0));
        assertEquals(Long.valueOf(0), monitoringPool.getConsumed());
        consumerCurator.get(parentSystem.getId());
        assertEquals(0, parentSystem.getEntitlementCount());
    }

    @Override
    protected Module getGuiceOverrideModule() {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(Enforcer.class).to(EntitlementRules.class);
                eventSink = Mockito.mock(EventSink.class);
                bind(EventSink.class).toInstance(eventSink);
            }
        };
    }

    @Test
    public void testRevocationRevokesEntitlementCertSerial() throws Exception {
        AutobindData data = new AutobindData(parentSystem, o)
            .on(new Date())
            .forProducts(Set.of(monitoring.getId()));

        Entitlement e = poolManager.entitleByProducts(data).get(0);
        CertificateSerial serial = e.getCertificates().iterator().next().getSerial();
        this.poolService.revokeEntitlement(e);

        List<Entitlement> entitlements = entitlementCurator.listByConsumer(parentSystem);
        assertTrue(entitlements.isEmpty());

        CertificateSerial revoked = certSerialCurator.get(serial.getId());
        assertTrue(revoked.isRevoked(), "cert serial should be marked as revoked once deleted!");
    }

}
