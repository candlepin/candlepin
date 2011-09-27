/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerInfo;
import org.fedoraproject.candlepin.model.OwnerInfoCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.fedoraproject.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * OwnerInfoCuratorTest
 */
public class OwnerInfoCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Pool pool1;
    private Pool pool2;
    private OwnerInfoCurator ownerInfoCurator;

    @Before
    public void setUp() {
        ownerInfoCurator = injector.getInstance(OwnerInfoCurator.class);
        owner = createOwner();
        ownerCurator.create(owner);

        Product product1 = TestUtil.createProduct();
        productCurator.create(product1);

        pool1 = createPoolAndSub(owner, product1, 1L,
            Util.yesterday(), Util.tomorrow());
        poolCurator.create(pool1);

        Product product2 = TestUtil.createProduct();
        productCurator.create(product2);

        pool2 = createPoolAndSub(owner, product2, 1L,
            Util.yesterday(), Util.tomorrow());
        poolCurator.create(pool2);

        ConsumerType consumerType = new ConsumerType("system");
        consumerTypeCurator.create(consumerType);

        consumerType = new ConsumerType("domain");
        consumerTypeCurator.create(consumerType);
    }

    @Test
    public void testOwnerInfoNoConsumers() {
        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerInfoOneSystemNoEntitlements() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerInfoOneSystemEntitlement() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerInfoOneSystemEntitlementWithQuantityOfTwo() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(2);
        entitlementCurator.create(entitlement);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 2);
                put("domain", 0);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerInfoOneOfEachEntitlement() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);
        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);

        type = consumerTypeCurator.lookupByLabel("domain");
        consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);

        cert = createEntitlementCertificate("fake", "fake");
        entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedConsumers = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 1);
            }
        };
        Map<String, Integer> expectedEntitlementsConsumed = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 1);
            }
        };

        assertEquals(expectedConsumers, info.getConsumerCounts());
        assertEquals(expectedEntitlementsConsumed, info.getEntitlementsConsumedByType());
    }

    @Test
    public void testOwnerPoolEntitlementCountPoolOnly() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("domain");
        pool1.setAttribute("requires_consumer_type", type.getLabel());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());

    }

    @Test
    public void testOwnerPoolEntitlementCountProductOnly() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("requires_consumer_type", "");
        Product prod = productCurator.lookupById(pool1.getProductId());
        prod.setAttribute("requires_consumer_type", type.getLabel());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 1);
                put("domain", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolEntitlementCountBoth() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("domain");
        ConsumerType type2 = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("requires_consumer_type", type.getLabel());
        Product prod = productCurator.lookupById(pool1.getProductId());
        prod.setAttribute("requires_consumer_type", type2.getLabel());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());
    }

    @Test
    public void testConsumerTypeCountByPoolExcludesFuturePools() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("requires_consumer_type", type.getLabel());
        pool1.setStartDate(Util.tomorrow());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());
    }

    @Test
    public void testConsumerTypeCountByPoolExcludesExpiredPools() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("requires_consumer_type", type.getLabel());
        pool1.setEndDate(Util.yesterday());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 0);
                put("domain", 0);
            }
        };

        assertEquals(expectedPoolCount, info.getConsumerTypeCountByPool());

    }

    @Test
    public void testOwnerPoolEnabledCountPoolOnly() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("domain");
        pool1.setAttribute("enabled_consumer_types", type.getLabel());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("domain", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolEnabledCountProductOnly() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("enabled_consumer_types", "");
        Product prod = productCurator.lookupById(pool1.getProductId());
        prod.setAttribute("enabled_consumer_types", type.getLabel());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("system", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolEnabledCountBoth() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("domain");
        ConsumerType type2 = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("enabled_consumer_types", type.getLabel());
        Product prod = productCurator.lookupById(pool1.getProductId());
        prod.setAttribute("enabled_consumer_types", type2.getLabel());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>() {
            {
                put("domain", 1);
                put("system", 1);
            }
        };

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testEnabledConsumerTypeCountByPoolExcludesFuturePools() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("enabled_consumer_types", type.getLabel());
        pool1.setStartDate(Util.tomorrow());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>();

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testEnabledConsumerTypeCountByPoolExcludesExpiredPools() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("enabled_consumer_types", type.getLabel());
        pool1.setEndDate(Util.yesterday());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>();

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerPoolMultiEnabledCount() {
        ConsumerType type1 = consumerTypeCurator.lookupByLabel("domain");
        ConsumerType type2 = consumerTypeCurator.lookupByLabel("system");
        pool1.setAttribute("enabled_consumer_types", type1.getLabel() +
                           "," + type2.getLabel());
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

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
        pool1.setAttribute("enabled_consumer_types", "non-type");
        owner.addEntitlementPool(pool1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, Integer> expectedPoolCount = new HashMap<String, Integer>();

        assertEquals(expectedPoolCount, info.getEnabledConsumerTypeCountByPool());
    }

    @Test
    public void testOwnerInfoEntitlementsConsumedByFamilyPutsFamilylessInNone() {
        owner.addEntitlementPool(pool1);

        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);
        pool1.getEntitlements().add(entitlement);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, OwnerInfo.ConsumptionTypeCounts> expected =
            new HashMap<String, OwnerInfo.ConsumptionTypeCounts>() {
                {
                    put("none", new OwnerInfo.ConsumptionTypeCounts(1, 0));
                }
            };

        assertEquals(expected, info.getEntitlementsConsumedByFamily());
    }

    @Test
    public void testOwnerInfoEntitlementsConsumedByFamilySortsByFamily() {
        owner.addEntitlementPool(pool1);

        Product prod = productCurator.lookupById(pool1.getProductId());
        prod.setAttribute("product_family", "test family");

        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);
        pool1.getEntitlements().add(entitlement);



        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, OwnerInfo.ConsumptionTypeCounts> expected =
            new HashMap<String, OwnerInfo.ConsumptionTypeCounts>() {
                {
                    put("test family", new OwnerInfo.ConsumptionTypeCounts(1, 0));
                }
            };

        assertEquals(expected, info.getEntitlementsConsumedByFamily());
    }

    @Test
    public void testOwnerInfoEntitlementsConsumedByFamilySeperatesVirtAndPhysical() {
        // other tests look at physical, so just do virtual
        owner.addEntitlementPool(pool1);

        Product prod = productCurator.lookupById(pool1.getProductId());
        prod.setAttribute("virt_only", "true");

        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlement.setQuantity(1);
        entitlementCurator.create(entitlement);
        pool1.getEntitlements().add(entitlement);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);

        Map<String, OwnerInfo.ConsumptionTypeCounts> expected =
            new HashMap<String, OwnerInfo.ConsumptionTypeCounts>() {
                {
                    put("none", new OwnerInfo.ConsumptionTypeCounts(0, 1));
                }
            };

        assertEquals(expected, info.getEntitlementsConsumedByFamily());

    }

    @Test
    public void testConsumerGuestCount() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer guest1 = new Consumer("test-consumer", "test-user", owner, type);
        guest1.setFact("virt.is_guest", "true");
        consumerCurator.create(guest1);

        Consumer guest2 = new Consumer("test-consumer", "test-user", owner, type);
        guest2.setFact("virt.is_guest", "true");
        consumerCurator.create(guest2);

        Consumer physical1 = new Consumer("test-consumer", "test-user", owner, type);
        physical1.setFact("virt.is_guest", "false");
        consumerCurator.create(physical1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);
        assertEquals((Integer) 2, info.getConsumerGuestCounts().get(OwnerInfo.GUEST));
        assertEquals((Integer) 1, info.getConsumerGuestCounts().get(OwnerInfo.PHYSICAL));


        // Create another owner to make sure we don't see another owners consumers:
        Owner anotherOwner = createOwner();
        ownerCurator.create(anotherOwner);
        info = ownerInfoCurator.lookupByOwner(anotherOwner);
        assertEquals((Integer) 0, info.getConsumerGuestCounts().get(OwnerInfo.GUEST));
        assertEquals((Integer) 0, info.getConsumerGuestCounts().get(OwnerInfo.PHYSICAL));

    }

    @Test
    public void testConsumerCountsByEntitlementStatus() {
        final String entitlementsValid = "True";
        final String entitlementsInvalid = "False";

        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer1 = new Consumer("test-consumer", "test-user", owner, type);
        consumer1.setFact("system.entitlements_valid", entitlementsValid);
        consumerCurator.create(consumer1);

        Consumer consumer2 = new Consumer("test-consumer", "test-user", owner, type);
        consumer2.setFact("system.entitlements_valid", entitlementsInvalid);
        consumerCurator.create(consumer2);

        Consumer consumer3 = new Consumer("test-consumer", "test-user", owner, type);
        consumer3.setFact("system.entitlements_valid", entitlementsValid);
        consumerCurator.create(consumer3);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);
        assertEquals((Integer) 2, info.getConsumerCountByStatus(entitlementsValid));
        assertEquals((Integer) 1, info.getConsumerCountByStatus(entitlementsInvalid));
    }

    @Test
    public void testConsumerCountsByEntitlementStatusReturnsZeroIfStatusWasNotDefined() {
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer1 = new Consumer("test-consumer", "test-user", owner, type);
        consumer1.setFact("system.entitlements_valid", "Known Status");
        consumerCurator.create(consumer1);

        OwnerInfo info = ownerInfoCurator.lookupByOwner(owner);
        assertEquals((Integer) 0, info.getConsumerCountByStatus("Unknown Status"));
    }
}
