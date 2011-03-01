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

import java.util.HashMap;
import java.util.Map;

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
import org.junit.Before;
import org.junit.Test;

/**
 * OwnerInfoCuratorTest
 */
public class OwnerInfoCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Consumer consumer;
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
            dateSource.currentDate(), dateSource.currentDate());
        poolCurator.create(pool1);

        Product product2 = TestUtil.createProduct();
        productCurator.create(product2);

        pool2 = createPoolAndSub(owner, product2, 1L,
            dateSource.currentDate(), dateSource.currentDate());
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
    public void testOwnerInfoOneOfEachEntitlement() {        
        ConsumerType type = consumerTypeCurator.lookupByLabel("system");
        Consumer consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);
        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool1, cert);
        entitlementCurator.create(entitlement);

        type = consumerTypeCurator.lookupByLabel("domain");
        consumer = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(consumer);
        
        cert = createEntitlementCertificate("fake", "fake");
        entitlement = createEntitlement(owner, consumer, pool1, cert);
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

}
