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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.UsernameConsumersPermission;
import org.candlepin.dto.api.server.v1.ConsumptionTypeCountsDTO;
import org.candlepin.dto.api.server.v1.OwnerInfo;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * OwnerInfoCuratorTest
 */
public class OwnerInfoCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Pool pool1;

    @BeforeEach
    public void setUp() {
        owner = this.createOwner();

        Product product1 = this.createProduct();

        pool1 = createPool(owner, product1, 1L, Util.yesterday(), Util.tomorrow());
        poolCurator.create(pool1);

        Product product2 = this.createProduct();

        ConsumerType consumerType = new ConsumerType("system");
        consumerTypeCurator.create(consumerType);

        consumerType = new ConsumerType("domain");
        consumerTypeCurator.create(consumerType);

        consumerType = new ConsumerType("uebercert");
        consumerTypeCurator.create(consumerType);
    }

    @Test
    public void testOwnerInfoNoConsumers() {
        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
                put("uebercert", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerInfoOneSystemNoEntitlements() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
                put("uebercert", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerInfoOneSystemEntitlement() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
                put("uebercert", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerInfoOneSystemEntitlementWithQuantityOfTwo() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(2);
        entitlementCurator.create(entitlement);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
                put("uebercert", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 2);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerInfoOneOfEachEntitlement() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);
        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);

        type = consumerTypeCurator.getByLabel("domain");
        consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        cert = createEntitlementCertificate("fake", "fake");
        entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 1);
                put("uebercert", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 1);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerPoolEntitlementCountPoolOnly() {
        ConsumerType type = consumerTypeCurator.getByLabel("domain");
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, type.getLabel());
        owner.addPool(pool1);
        this.poolCurator.merge(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 1);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());

    }

    @Test
    public void testOwnerPoolEntitlementCountProductOnly() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Product prod = new Product("sysProd", "sysProd")
            .setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type.getLabel());
        this.createProduct(prod);
        pool1.setProduct(prod);

        owner.addPool(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolEntitlementPoolOverridesProduct() {
        ConsumerType type = consumerTypeCurator.getByLabel("domain");
        ConsumerType type2 = consumerTypeCurator.getByLabel("system");
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, type.getLabel());
        Product prod = new Product("sysProd", "sysProd")
            .setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type2.getLabel());
        this.createProduct(prod);
        pool1.setProduct(prod);
        owner.addPool(pool1);
        this.poolCurator.merge(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 1);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());
    }

    @Test
    public void testConsumerTypeCountByPoolExcludesFuturePools() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, type.getLabel());
        pool1.setStartDate(Util.tomorrow());
        owner.addPool(pool1);
        this.poolCurator.merge(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());
    }

    @Test
    public void testConsumerTypeCountByPoolExcludesExpiredPools() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, type.getLabel());
        pool1.setEndDate(Util.yesterday());
        owner.addPool(pool1);
        this.poolCurator.merge(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());

    }

    @Test
    public void testConsumerTypeCountByPoolPutsDefaultsIntoSystem() {
        owner.addPool(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolEnabledCountPoolOnly() {
        ConsumerType type = consumerTypeCurator.getByLabel("domain");
        pool1.setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type.getLabel());
        owner.addPool(pool1);
        this.poolCurator.merge(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("domain", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolEnabledCountProductOnly() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Product prod = new Product("sysProd", "sysProd")
            .setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type.getLabel());
        this.createProduct(prod);
        pool1.setProduct(prod);
        owner.addPool(pool1);
        this.poolCurator.merge(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolEnabledPoolOverridesProduct() {
        ConsumerType type = consumerTypeCurator.getByLabel("domain");
        ConsumerType type2 = consumerTypeCurator.getByLabel("system");
        pool1.setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type.getLabel());
        Product prod = new Product("sysProd", "sysProd")
            .setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type2.getLabel());
        this.createProduct(prod);
        pool1.setProduct(prod);

        owner.addPool(pool1);
        this.poolCurator.merge(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("domain", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testEnabledConsumerTypeCountByPoolExcludesFuturePools() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        pool1.setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type.getLabel());
        pool1.setStartDate(Util.tomorrow());
        owner.addPool(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<>();

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testEnabledConsumerTypeCountByPoolExcludesExpiredPools() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        pool1.setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type.getLabel());
        pool1.setEndDate(Util.yesterday());
        owner.addPool(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<>();

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolMultiEnabledCount() {
        ConsumerType type1 = consumerTypeCurator.getByLabel("domain");
        ConsumerType type2 = consumerTypeCurator.getByLabel("system");
        pool1.setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, type1.getLabel() + "," + type2.getLabel());
        owner.addPool(pool1);
        this.poolCurator.merge(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("domain", 1);
                put("system", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolEnabledZeroCount() {
        pool1.setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, "non-type");
        owner.addPool(pool1);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<>();

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }



    @Test
    public void testOwnerInfoEntitlementsConsumedByFamilyPutsFamilylessInNone() {
        owner.addPool(pool1);

        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);
        pool1.getEntitlements().add(entitlement);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, ConsumptionTypeCountsDTO> expected = new HashMap<>();
        expected.put("none", new ConsumptionTypeCountsDTO().physical(1).guest(0));

        assertEquals(expected, info.getEntitlementsConsumedByFamily());
    }

    @Test
    public void testOwnerInfoEntitlementsConsumedByFamilyPoolOverridesProduct() {
        owner.addPool(pool1);

        pool1.setAttribute(Pool.Attributes.PRODUCT_FAMILY, "test family");
        Product product = new Product("testProd", "testProd")
            .setAttribute(Pool.Attributes.PRODUCT_FAMILY, "bad test family");
        this.createProduct(product);
        pool1.setProduct(product);

        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);
        pool1.getEntitlements().add(entitlement);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, ConsumptionTypeCountsDTO> expected = new HashMap<>();
        expected.put("test family", new ConsumptionTypeCountsDTO().physical(1).guest(0));

        assertEquals(expected, info.getEntitlementsConsumedByFamily());
    }

    @Test
    public void testOwnerInfoEntitlementsConsumedByFamilySortsByFamily() {
        owner.addPool(pool1);

        Product product = TestUtil.createProduct()
            .setAttribute(Pool.Attributes.PRODUCT_FAMILY, "test family");
        this.createProduct(product);
        pool1.setProduct(product);

        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);
        pool1.getEntitlements().add(entitlement);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, ConsumptionTypeCountsDTO> expected = new HashMap<>();
        expected.put("test family", new ConsumptionTypeCountsDTO().physical(1).guest(0));

        assertEquals(expected, info.getEntitlementsConsumedByFamily());
    }

    @Test
    public void testOwnerInfoEntitlementsConsumedByFamilySeperatesVirtAndPhysical() {
        // other tests look at physical, so just do virtual
        owner.addPool(pool1);

        Product product = TestUtil.createProduct()
            .setAttribute(Pool.Attributes.VIRT_ONLY, "true");
        this.createProduct(product);
        pool1.setProduct(product);

        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);
        pool1.getEntitlements().add(entitlement);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, ConsumptionTypeCountsDTO> expected = new HashMap<>();
        expected.put("none", new ConsumptionTypeCountsDTO().physical(0).guest(1));

        assertEquals(expected, info.getEntitlementsConsumedByFamily());

    }

    @Test
    public void testOwnerInfoEntitlementsConsumedByFamilySeperatesVirtExplicitFamily() {
        owner.addPool(pool1);

        Product product = TestUtil.createProduct()
            .setAttribute(Pool.Attributes.PRODUCT_FAMILY, "test family")
            .setAttribute(Pool.Attributes.VIRT_ONLY, "true");
        this.createProduct(product);
        pool1.setProduct(product);

        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);
        pool1.getEntitlements().add(entitlement);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, ConsumptionTypeCountsDTO> expected = new HashMap<>();
        expected.put("test family", new ConsumptionTypeCountsDTO().physical(0).guest(1));

        assertEquals(expected, info.getEntitlementsConsumedByFamily());
    }

    @Test
    public void testConsumerGuestCount() {
        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer guest1 = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type)
            .setFact(Consumer.Facts.VIRT_IS_GUEST, "true");
        consumerCurator.create(guest1);

        Consumer guest2 = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type)
            .setFact(Consumer.Facts.VIRT_IS_GUEST, "true");
        consumerCurator.create(guest2);

        Consumer physical1 = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type)
            .setFact(Consumer.Facts.VIRT_IS_GUEST, "false");
        consumerCurator.create(physical1);

        // Second physical machine with no is_guest fact set.
        Consumer physical2 = new Consumer()
            .setName("test-consumer2")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(physical2);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);
        assertEquals((Integer) 2, info.getConsumerGuestCounts().get(OwnerInfoBuilder.GUEST));
        assertEquals((Integer) 2, info.getConsumerGuestCounts().get(OwnerInfoBuilder.PHYSICAL));


        // Create another owner to make sure we don't see another owners consumers:
        Owner anotherOwner = createOwner();
        ownerCurator.create(anotherOwner);
        info = ownerInfoCurator.getByOwner(anotherOwner);
        assertEquals((Integer) 0, info.getConsumerGuestCounts().get(OwnerInfoBuilder.GUEST));
        assertEquals((Integer) 0, info.getConsumerGuestCounts().get(OwnerInfoBuilder.PHYSICAL));
    }

    @Test
    public void testConsumerCountsByEntitlementStatus() {
        setupConsumerCountTest("test-user");

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);
        assertConsumerCountsByEntitlementStatus(info);
    }

    @Test
    public void testPermissionsAppliedWhenDeterminingConsumerCountsByEntStatus() {
        User mySystemsUser = setupOnlyMyConsumersPrincipal();
        setupConsumerCountTest("test-user");
        setupConsumerCountTest(mySystemsUser.getUsername());

        // Should only get the counts for a single setup case above.
        // The test-user consumers should be ignored.
        OwnerInfo info = ownerInfoCurator.getByOwner(owner);
        assertConsumerCountsByEntitlementStatus(info);
    }

    @Test
    public void testPermissionsAppliedForConsumerCounts() {
        User mySystemsUser = setupOnlyMyConsumersPrincipal();

        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("my-system-1")
            .setUsername(mySystemsUser.getUsername())
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        consumer = new Consumer()
            .setName("not-my-system")
            .setUsername("another-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedConsumers = Map.of(
            "system", 1,
            "domain", 0,
            "uebercert", 0);

        assertEquals(expectedConsumers, info.getConsumerCounts());
    }

    @Test
    public void testPermissionsAppliedForConsumerTypeEntitlementEntitlementsConsumedByType() {
        User mySystemsUser = setupOnlyMyConsumersPrincipal();

        ConsumerType type = consumerTypeCurator.getByLabel("system");
        Consumer consumer = new Consumer()
            .setName("my-system-1")
            .setUsername(mySystemsUser.getUsername())
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate myConsumersCert = createEntitlementCertificate("mine",
            "mine");
        Entitlement myConsumerEntitlement = createEntitlement(owner, consumer, pool1,
            myConsumersCert);
        myConsumerEntitlement.setQuantity(2);
        entitlementCurator.create(myConsumerEntitlement);
        consumerCurator.merge(consumer);

        consumer = new Consumer()
            .setName("not-my-system")
            .setUsername("another-user")
            .setOwner(owner)
            .setType(type);
        consumerCurator.create(consumer);

        EntitlementCertificate otherCert = createEntitlementCertificate("other", "other");
        Entitlement otherEntitlement = createEntitlement(owner, consumer, pool1, otherCert);
        otherEntitlement.setQuantity(1);
        entitlementCurator.create(otherEntitlement);
        consumerCurator.merge(consumer);

        OwnerInfo info = ownerInfoCurator.getByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 2);
                put("domain", 0);
                put("uebercert", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    private void setupConsumerCountTest(String username) {
        ConsumerType systemType = consumerTypeCurator.getByLabel("system");

        Consumer consumer1 = new Consumer()
            .setName("test-consumer1")
            .setUsername(username)
            .setOwner(owner)
            .setType(systemType)
            .setEntitlementStatus(ComplianceStatus.GREEN);

        Consumer consumer2 = new Consumer()
            .setName("test-consumer2")
            .setUsername(username)
            .setOwner(owner)
            .setType(systemType)
            .setEntitlementStatus(ComplianceStatus.RED);

        Consumer consumer3 = new Consumer()
            .setName("test-consumer3")
            .setUsername(username)
            .setOwner(owner)
            .setType(systemType)
            .setEntitlementStatus(ComplianceStatus.GREEN);

        Consumer consumer4 = new Consumer()
            .setName("test-consumer3")
            .setUsername(username)
            .setOwner(owner)
            .setType(systemType)
            .setEntitlementStatus(ComplianceStatus.YELLOW);

        consumerCurator.create(consumer1);
        consumerCurator.create(consumer2);
        consumerCurator.create(consumer3);
        consumerCurator.create(consumer4);
    }

    private void assertConsumerCountsByEntitlementStatus(OwnerInfo info) {
        assertEquals((Integer) 2, info.getConsumerCountsByComplianceStatus().get(ComplianceStatus.GREEN));
        assertEquals((Integer) 1, info.getConsumerCountsByComplianceStatus().get(ComplianceStatus.RED));
        assertEquals((Integer) 1, info.getConsumerCountsByComplianceStatus().get(ComplianceStatus.YELLOW));
    }

    private User setupOnlyMyConsumersPrincipal() {
        Set<Permission> perms = new HashSet<>();
        User u = new User("MySystemsAdmin", "passwd");
        perms.add(new UsernameConsumersPermission(u, owner));
        Principal p = new UserPrincipal(u.getUsername(), perms, false);
        setupPrincipal(p);
        return u;
    }

    @Test
    public void testOwnerInfoBuilderWithNoGuests() {
        OwnerInfo info = new OwnerInfoBuilder().build();
        assertEquals((Integer) 0, info.getConsumerGuestCounts().get(OwnerInfoBuilder.GUEST));
        assertEquals((Integer) 0, info.getConsumerGuestCounts().get(OwnerInfoBuilder.PHYSICAL));
    }
}
